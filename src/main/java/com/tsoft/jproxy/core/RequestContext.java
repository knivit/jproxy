package com.tsoft.jproxy.core;

import java.util.*;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.FastThreadLocal;

public class RequestContext {

	private static final FastThreadLocal<RequestContext> CONTEXT = new FastThreadLocal<RequestContext>() {
		@Override
		protected RequestContext initialValue() throws Exception {
			return new RequestContext();
		}
	};

	private final Map<String, LinkedList<Connection>> keepAlivedConns = new HashMap<>();

	private final FullHttpResponse errorResponse;
	private final FullHttpResponse notFoundResponse;
	
	private RequestContext() {
		errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
		errorResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errorResponse.content().readableBytes());
		errorResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

		notFoundResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
		notFoundResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, notFoundResponse.content().readableBytes());
		notFoundResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
	}

	public LinkedList<Connection> getKeepAlivedConns(String proxypass) {
		return keepAlivedConns.computeIfAbsent(proxypass, k -> new LinkedList<>());
	}

	public FullHttpResponse getErrorResponse() {
		return errorResponse;
	}

	public FullHttpResponse getNotFoundResponse() {
		return notFoundResponse;
	}
	
	public static LinkedList<Connection> getKeepAliveConns(String proxypass) {
		return CONTEXT.get().getKeepAlivedConns(proxypass);
	}

	public static FullHttpResponse errorResponse() {
		return CONTEXT.get().getErrorResponse().retain();
	}

	public static FullHttpResponse notFoundResponse() {
		return CONTEXT.get().getNotFoundResponse().retain();
	}
}
