package com.tsoft.jproxy.upstream;

import java.util.Iterator;
import java.util.LinkedList;

import com.tsoft.jproxy.response.ErrorResponseFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import com.tsoft.jproxy.core.UpStreamServer;
import com.tsoft.jproxy.core.AttributeKeys;
import com.tsoft.jproxy.core.Connection;
import com.tsoft.jproxy.core.RequestContext;

@Slf4j
@RequiredArgsConstructor
public class UpStreamHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

	private final ErrorResponseFactory errorResponseFactory = new ErrorResponseFactory();

	private final UpStreamServer upStreamServer;
	private final String proxyPass;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
		Channel upstream = ctx.channel();
		String requestUri = upstream.attr(AttributeKeys.REQUEST_URI).get();
		log.info("channelRead0 '{}' channel {}", requestUri, upstream);

		// get context and clear
		Channel downstream = upstream.attr(AttributeKeys.DOWNSTREAM_CHANNEL_KEY).getAndSet(null);
		boolean keepAlive = upstream.attr(AttributeKeys.KEEP_ALIVED_KEY).getAndSet(null);

		LinkedList<Connection> conns = RequestContext.getKeepAliveConns(proxyPass);

		if (conns.size() == upStreamServer.getKeepAlive()) {
			// the least recently used connection are closed
			log.info("[{}] cached connections exceed the keep alive [{}], the least recently used connection are closed",
					proxyPass, upStreamServer.getKeepAlive());

			Channel tmp = conns.pollFirst().getChannel();
			tmp.attr(AttributeKeys.UPSTREAM_ACTIVE_CLOSE_KEY).set(true);
			tmp.close();
		}

		conns.addLast(new Connection(upStreamServer, upstream));

		if (keepAlive) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			downstream.writeAndFlush(response.retain(), downstream.voidPromise());
		} else {
			// close the downstream connection
			downstream.writeAndFlush(response.retain()).addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Channel upstream = ctx.channel();
		boolean activeClose = false;

		if (upstream.hasAttr(AttributeKeys.UPSTREAM_ACTIVE_CLOSE_KEY)
				&& upstream.attr(AttributeKeys.UPSTREAM_ACTIVE_CLOSE_KEY).get()){
			activeClose = true;
		}

		String requestUri = upstream.attr(AttributeKeys.REQUEST_URI).get();
		log.warn("upstream '{}' channel[{}] inactive, activeClose:{}", requestUri, upstream, activeClose);

		Channel downstream = upstream.attr(AttributeKeys.DOWNSTREAM_CHANNEL_KEY).get();
		Boolean keepAlive = upstream.attr(AttributeKeys.KEEP_ALIVED_KEY).get();

		if (downstream != null && keepAlive != null) {
			FullHttpResponse errorResponse = errorResponseFactory.create();
			if (keepAlive) {
				downstream.writeAndFlush(errorResponse, downstream.voidPromise());
			} else {
				downstream.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE);
			}
		} else {
			log.info("remove inactive '{}' channel[{}] from cached conns", requestUri, upstream);
			LinkedList<Connection> conns = RequestContext.getKeepAliveConns(proxyPass);

			Connection tmp;
			for (Iterator<Connection> it = conns.iterator(); it.hasNext();) {
				tmp = it.next();

				// find the inactive connection
				if (upStreamServer == tmp.getUpStreamServer()) {
					it.remove();
					break;
				}
			}
		}

		super.channelInactive(ctx);
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		Channel upstream = ctx.channel();
		String requestUri = upstream.attr(AttributeKeys.REQUEST_URI).get();
		log.warn("upstream '{}' channel[{}] writability changed, isWritable: {}", requestUri, upstream, upstream.isWritable());

		super.channelWritabilityChanged(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		Channel upstream = ctx.channel();
		String requestUri = upstream.attr(AttributeKeys.REQUEST_URI).get();

		log.error("upstream '{}' channel[{}] exception caught", requestUri, upstream, cause);
	}
}
