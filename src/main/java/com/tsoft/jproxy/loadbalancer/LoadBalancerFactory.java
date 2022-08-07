package com.tsoft.jproxy.loadbalancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.tsoft.jproxy.core.JProxyConfig;
import com.tsoft.jproxy.core.UpStreamServer;

public class LoadBalancerFactory {

	private final Map<String, RoundRobin> roundRobinMap = new HashMap<>();

	public void init(JProxyConfig config) {
		Map<String, List<UpStreamServer>> upstreams = config.getUpStreams();
		if (upstreams == null) {
			return;
		}

		for (Entry<String, List<UpStreamServer>> upstreamEntry : upstreams.entrySet()) {
			roundRobinMap.put(upstreamEntry.getKey(), new RoundRobin(upstreamEntry.getValue()));
		}
	}

	public RoundRobin getBalancer(String proxypass) {
		return roundRobinMap.get(proxypass);
	}
}
