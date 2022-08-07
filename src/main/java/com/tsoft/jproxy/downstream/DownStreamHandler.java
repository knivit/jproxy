package com.tsoft.jproxy.downstream;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.tsoft.jproxy.conf.Location;
import com.tsoft.jproxy.downstream.plugin.DownStreamPlugin;
import com.tsoft.jproxy.downstream.plugin.SetHeadersPlugin;
import com.tsoft.jproxy.loadbalancer.LoadBalancer;
import lombok.extern.slf4j.Slf4j;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import com.tsoft.jproxy.core.JProxyConfig;
import com.tsoft.jproxy.core.UpStreamServer;
import com.tsoft.jproxy.core.AttributeKeys;
import com.tsoft.jproxy.core.Connection;
import com.tsoft.jproxy.core.RequestContext;
import com.tsoft.jproxy.upstream.JProxyUpStreamChannelInitializer;
import com.tsoft.jproxy.loadbalancer.LoadBalancerFactory;

@Slf4j
@ChannelHandler.Sharable
public class DownStreamHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final int MAX_ATTEMPTS = 3;

	private final JProxyConfig config;

	private final LoadBalancerFactory loadBalancerFactory;
	private final ProxyLocationMatcher proxyLocationMatcher = new ProxyLocationMatcher();

	private static final List<DownStreamPlugin> plugins = Arrays.asList(
		new SetHeadersPlugin()
	);

	public DownStreamHandler(JProxyConfig config, LoadBalancerFactory loadBalancerFactory) {
		this.config = config;
		this.loadBalancerFactory = loadBalancerFactory;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		Channel downstream = ctx.channel();

		boolean keepAlive = HttpUtil.isKeepAlive(request);
		HttpHeaders requestHeaders = request.headers();

		// get Host header
		String serverName = requestHeaders.get(HttpHeaderNames.HOST);

		// get proxy location
		Location location = proxyLocationMatcher.getProxyLocation(config, serverName, request.uri());

		// get roundRobin
		String proxyPass;
		LoadBalancer loadBalancer;
		UpStreamServer upStreamServer;
		if (location == null ||
				(proxyPass = location.getProxyPass()) == null ||
				(loadBalancer = loadBalancerFactory.getBalancer(proxyPass)) == null ||
				(upStreamServer = loadBalancer.next()) == null) {
			// return 404
			notFound(ctx, keepAlive);
			return;
		}

		// rewrite http request (keep alive to upstream)
		request.setProtocolVersion(HttpVersion.HTTP_1_1);
		requestHeaders.remove(HttpHeaderNames.CONNECTION);

		// invoke plugins
		for (DownStreamPlugin plugin : plugins) {
			plugin.init(location, request);
		}

		// increase refCount
		request.retain();

		// proxy request
		proxy(upStreamServer, proxyPass, downstream, request, keepAlive, MAX_ATTEMPTS);
	}

	private void proxy(UpStreamServer upStreamServer, String proxyPass, Channel downstream, FullHttpRequest request, boolean keepAlive, int maxAttempts) {
		// get connection from cache
		Connection connection = getConn(upStreamServer, proxyPass);

		if (connection == null) {
			createConnAndSendRequest(downstream, upStreamServer, proxyPass, request, keepAlive, maxAttempts);
		} else {
			// use the cached connection
			setContextAndRequest(upStreamServer, proxyPass, request, connection.getChannel(), downstream, keepAlive, false, maxAttempts);
		}
	}

	private void createConnAndSendRequest(Channel downstream, UpStreamServer upStreamServer, String proxyPass, FullHttpRequest request,
										  boolean keepAlived, int maxAttempts) {
		Bootstrap b = new Bootstrap();
		b.group(downstream.eventLoop());
		b.channel(downstream.getClass());

		b.option(ChannelOption.TCP_NODELAY, true);
		b.option(ChannelOption.SO_KEEPALIVE, true);
		// default is pooled direct
		// ByteBuf(io.netty.util.internal.PlatformDependent.DIRECT_BUFFER_PREFERRED)
		b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
		// 32kb(for massive long connections, See
		// http://www.infoq.com/cn/articles/netty-million-level-push-service-design-points)
		// 64kb(RocketMq remoting default value)
		b.option(ChannelOption.SO_SNDBUF, 32 * 1024);
		b.option(ChannelOption.SO_RCVBUF, 32 * 1024);
		// temporary settings, need more tests
		b.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024));
		// default is true, reduce thread context switching
		b.option(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, true);

		b.handler(new JProxyUpStreamChannelInitializer(upStreamServer, proxyPass));

		ChannelFuture connectFuture = b.connect(upStreamServer.getIp(), upStreamServer.getPort());

		connectFuture.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					setContextAndRequest(upStreamServer, proxyPass, request, future.channel(), downstream, keepAlived, true,
							maxAttempts);
				} else {
					if (maxAttempts > 0) {
						proxy(upStreamServer, proxyPass, downstream, request, keepAlived, maxAttempts - 1);
					} else {
						request.release();
						downstream.writeAndFlush(RequestContext.errorResponse(), downstream.voidPromise());
					}
				}
			}
		});
	}

	private Connection getConn(UpStreamServer upStreamServer, String proxyPass) {
		LinkedList<Connection> conns = RequestContext.getKeepAliveConns(proxyPass);
		Connection connection = null;

        Connection tmp;
		for (Iterator<Connection> it = conns.iterator(); it.hasNext();) {
			tmp = it.next();

			// find the matched keepalived connection
			if (upStreamServer.equals(tmp.getUpStreamServer())) {
				it.remove();
				connection = tmp;
				break;
			}
		}

		return connection;
	}

	private void notFound(ChannelHandlerContext ctx, boolean keepAlived) {
		if (keepAlived) {
			ctx.writeAndFlush(RequestContext.notFoundResponse(), ctx.voidPromise());
		} else {
			ctx.writeAndFlush(RequestContext.notFoundResponse()).addListener(ChannelFutureListener.CLOSE);
		}
	}

	private void setContextAndRequest(UpStreamServer upStreamServer, String proxyPass, FullHttpRequest request, Channel upstream,
									  Channel downstream, boolean keepAlived, final boolean newConn, final int maxAttempts) {
		// set request context
		upstream.attr(AttributeKeys.DOWNSTREAM_CHANNEL_KEY).set(downstream);
		upstream.attr(AttributeKeys.KEEP_ALIVED_KEY).set(keepAlived);

		request.retain();
		upstream.writeAndFlush(request).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (!future.isSuccess()) {
					if (maxAttempts > 0) {
						proxy(upStreamServer, proxyPass, downstream, request, keepAlived, maxAttempts - 1);
					} else {
						downstream.writeAndFlush(RequestContext.errorResponse(), downstream.voidPromise());
						log.error("{} upstream channel[{}] write to backed fail",
								newConn ? "new" : "cached", future.channel(), future.cause());
					}
				} else {
					request.release();
				}
			}
		});
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		log.warn("downstream channel[{}] writability changed, isWritable: {}", ctx.channel(),
				ctx.channel().isWritable());
		super.channelWritabilityChanged(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		log.warn("downstream channel[{}] inactive", ctx.channel());
		super.channelInactive(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log.error("downstream channel[{}] exceptionCaught", ctx.channel(), cause);
	}
}
