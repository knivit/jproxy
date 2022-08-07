package com.tsoft.jproxy.conf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Config {

    @JsonProperty("listen")
    private int listen;

    @JsonProperty("keep-alive-timeout-sec")
    private int keepAliveTimeout;

    @JsonProperty("worker-threads")
    private String workerThreads;

    @JsonProperty("worker-connections")
    private int workerConnections;

    @JsonProperty("locations")
    private Map<String, List<Location>> locations;

    @JsonProperty("upstreams")
    private Map<String, UpStream> upStreams;
}
