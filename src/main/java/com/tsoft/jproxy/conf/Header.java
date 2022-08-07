package com.tsoft.jproxy.conf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Header {

    @JsonProperty("name")
    private String name;

    @JsonProperty("value")
    private String value;
}
