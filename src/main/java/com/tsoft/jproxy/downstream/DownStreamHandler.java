package com.tsoft.jproxy.downstream;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.tsoft.jproxy.conf.Location;
import com.tsoft.jproxy.downstream.plugin.DownStreamPlugin;
import com.tsoft.jproxy.downstream.plugin.SetHeadersPlugin;
import com.tsoft.jproxy.loadbalancer.LoadBalancer;
import com.tsoft.jproxy.response.NotFoundResponseFactory;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import com.tsoft.jproxy.core.JProxyConfig;
import com.tsoft.jproxy.core.UpStreamServer;
import com.tsoft.jproxy.core.AttributeKeys;
import com.tsoft.jproxy.core.Connection;
import com.tsoft.jproxy.core.RequestContext;
import com.tsoft.jproxy.loadbalancer.LoadBalancerFactory;

@Slf4j
@ChannelHandler.Sharable
public class DownStreamHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private static final int MAX_ATTEMPTS = 3;

	private final JProxyConfig config;

	private final LoadBalancerFactory loadBalancerFactory;
	private final ProxyLocationMatcher proxyLocationMatcher = new ProxyLocationMatcher();
	private final NotFoundResponseFactory notFoundResponseFactory = new NotFoundResponseFactory();

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
		downstream.attr(AttributeKeys.REQUEST_URI).set(request.uri());

		String requestUri = downstream.attr(AttributeKeys.REQUEST_URI).get();
		log.info("channelRead0 '{}' channel {}", requestUri, downstream);

		boolean keepAlive = HttpUtil.isKeepAlive(request);
		HttpHeaders requestHeaders = request.headers();

		// get Host header
		String serverName = requestHeaders.get(HttpHeaderNames.HOST);

		// get proxy location
		Location location = proxyLocationMatcher.getProxyLocation(config, serverName, request.uri());

		// get load balancer
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
		proxy(upStreamServer, proxyPass, downstream, request, keepAlive);
	}

	private void proxy(UpStreamServer upStreamServer, String proxyPass, Channel downstream, FullHttpRequest request, boolean keepAlive) {
		// get connection from cache
		Connection connection = getConn(upStreamServer, proxyPass);

		if (connection == null) {
			UpStreamConnectionCreator connectionCreator = new UpStreamConnectionCreator(downstream, upStreamServer, proxyPass, request, keepAlive);
			connectionCreator.connect();
		} else {
			// use the cached connection
			UpStreamRequestSender requestSender = new UpStreamRequestSender(request, connection.getChannel(), downstream, keepAlive);
			requestSender.send();
		}
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
		FullHttpResponse notFoundResponse = notFoundResponseFactory.create();
		if (keepAlived) {
			ctx.writeAndFlush(notFoundResponse, ctx.voidPromise());
		} else {
			ctx.writeAndFlush(notFoundResponse).addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Channel downstream = ctx.channel();
		String requestUri = downstream.attr(AttributeKeys.REQUEST_URI).get();
		log.warn("downstream '{}' channel[{}] inactive", requestUri, downstream);

		super.channelInactive(ctx);
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		Channel downstream = ctx.channel();
		String requestUri = downstream.attr(AttributeKeys.REQUEST_URI).get();
		log.warn("downstream '{}' channel[{}] writability changed, isWritable: {}", requestUri, downstream, downstream.isWritable());

		super.channelWritabilityChanged(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		Channel downstream = ctx.channel();
		String requestUri = downstream.attr(AttributeKeys.REQUEST_URI).get();
		log.error("downstream '{}' channel[{}] exception caught", requestUri, downstream, cause);
	}
}
