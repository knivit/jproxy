package com.tsoft.jproxy.conf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class UpStream {
    // the maximum number of idle keepalive connections to upstream servers
    // that are preserved in the cache of each worker process
    @JsonProperty("max-connections")
    private int keepAlive;

    @JsonProperty("servers")
    private List<String> servers;
}
