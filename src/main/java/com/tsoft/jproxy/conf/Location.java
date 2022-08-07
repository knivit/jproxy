package com.tsoft.jproxy.conf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Location {

    @JsonProperty("path")
    private String path;

    @JsonProperty("proxy-pass")
    private String proxyPass;

    @JsonProperty("set-headers")
    private List<Header> setHeaders;
}
