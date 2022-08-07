package com.tsoft.jproxy.core;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

import com.tsoft.jproxy.conf.Config;
import com.tsoft.jproxy.conf.Location;
import com.tsoft.jproxy.conf.UpStream;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Slf4j
@Data
@Accessors(chain = true)
public class JProxyConfig {

	private static final String UPSTREAM_POOL_PREFIX = "http://";

	private static final String WORKER_THREADS_AUTO = "auto";

	private static final int DEFAULT_HTTP_PORT = 80;

	private int listen;
	private int keepAliveTimeout;
	private int workerThreads;
	private int workerConnections;
	private Map<String, List<Location>> servers;
	private Map<String, List<UpStreamServer>> upStreams = new HashMap<>();

	public void parse(String path) {
		File configFile = new File(path);

		log.info("Reading configuration from: {}", configFile);

		if (!configFile.exists()) {
			throw new IllegalArgumentException(configFile + " file is missing");
		}

		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			parseConfig(mapper.readValue(new File(path), Config.class));
		} catch (Exception e) {
			throw new IllegalArgumentException("Error processing " + path, e);
		}
	}

	private void parseConfig(Config config) {
		listen = config.getListen();
		keepAliveTimeout = config.getKeepAliveTimeout();
		workerConnections = config.getWorkerConnections();
		String workers = config.getWorkerThreads();

		if (WORKER_THREADS_AUTO.equalsIgnoreCase(workers)) {
			workerThreads = Runtime.getRuntime().availableProcessors();
		} else {
			try {
				workerThreads = Integer.parseInt(workers);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("worker-threads invalid", e);
			}
		}

		servers = new HashMap<>();
		if (DEFAULT_HTTP_PORT != listen) {
			for (Entry<String, List<Location>> entry : config.getLocations().entrySet()) {
				servers.put(entry.getKey() + ":" + listen, entry.getValue());
			}
		} else {
			servers = config.getLocations();
		}

		Map<String, UpStream> us = new HashMap<>();
		for (Entry<String, UpStream> entry : config.getUpStreams().entrySet()) {
			us.put(UPSTREAM_POOL_PREFIX + entry.getKey(), entry.getValue());
		}

		for (Entry<String, UpStream> upstreamEntry : us.entrySet()) {
			List<String> hosts = upstreamEntry.getValue().getServers();
			if (hosts == null || hosts.isEmpty()) {
				continue;
			}

			List<UpStreamServer> upstreamServers = new ArrayList<>();
			for (String host : hosts) {
				upstreamServers.add(new UpStreamServer(host, upstreamEntry.getValue().getKeepAlive()));
			}

			upStreams.put(upstreamEntry.getKey(), upstreamServers);
		}
	}
}
