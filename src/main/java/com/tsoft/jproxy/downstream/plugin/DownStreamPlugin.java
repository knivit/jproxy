package com.tsoft.jproxy.downstream.plugin;

import com.tsoft.jproxy.conf.Location;
import io.netty.handler.codec.http.FullHttpRequest;

public interface DownStreamPlugin {

    void init(Location location, FullHttpRequest request);
}
