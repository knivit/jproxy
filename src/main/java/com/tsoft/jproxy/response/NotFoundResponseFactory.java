package com.tsoft.jproxy.response;

import io.netty.handler.codec.http.*;

public class NotFoundResponseFactory {

    public FullHttpResponse create() {
        FullHttpResponse notFoundResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        notFoundResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, notFoundResponse.content().readableBytes());
        notFoundResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        return notFoundResponse;
    }
}
