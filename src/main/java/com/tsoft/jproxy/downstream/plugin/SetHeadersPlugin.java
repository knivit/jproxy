package com.tsoft.jproxy.downstream.plugin;

import com.tsoft.jproxy.conf.Header;
import com.tsoft.jproxy.conf.Location;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.List;

public class SetHeadersPlugin implements DownStreamPlugin {

    @Override
    public void init(Location location, FullHttpRequest request) {
        List<Header> headers = location.getSetHeaders();
        if (headers == null) {
            return;
        }

        HttpHeaders requestHeaders = request.headers();
        for (Header header : headers) {
            requestHeaders.set(header.getName(), header.getValue());
        }
    }
}
