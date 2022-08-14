package com.tsoft.jproxy.core;

import java.util.*;

import io.netty.util.concurrent.FastThreadLocal;

public class RequestContext {

	private static final FastThreadLocal<RequestContext> CONTEXT = new FastThreadLocal<RequestContext>() {
		@Override
		protected RequestContext initialValue() throws Exception {
			return new RequestContext();
		}
	};

	private final Map<String, LinkedList<Connection>> keepAlivedConns = new HashMap<>();

	private LinkedList<Connection> getKeepAlivedConns(String proxypass) {
		return keepAlivedConns.computeIfAbsent(proxypass, k -> new LinkedList<>());
	}

	public static LinkedList<Connection> getKeepAliveConns(String proxypass) {
		return CONTEXT.get().getKeepAlivedConns(proxypass);
	}
}
