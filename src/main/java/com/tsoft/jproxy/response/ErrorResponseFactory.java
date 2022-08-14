package com.tsoft.jproxy.response;

import io.netty.handler.codec.http.*;

public class ErrorResponseFactory {

    public ErrorResponseFactory() { }

    public FullHttpResponse create() {
        FullHttpResponse errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        errorResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errorResponse.content().readableBytes());
        errorResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        return errorResponse;
    }
}
