package com.tsoft.jproxy.loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.tsoft.jproxy.core.UpStreamServer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RoundRobin implements LoadBalancer {

	private final AtomicInteger idx = new AtomicInteger();
	private final List<UpStreamServer> upStreamServers;

	@Override
	public UpStreamServer next() {
		int n = Math.abs(idx.getAndIncrement() % upStreamServers.size());
		return upStreamServers.get(n);
	}
}

