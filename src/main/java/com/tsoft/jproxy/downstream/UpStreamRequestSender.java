package com.tsoft.jproxy.downstream;

import com.tsoft.jproxy.core.AttributeKeys;
import com.tsoft.jproxy.response.ErrorResponseFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class UpStreamRequestSender {

    private final ErrorResponseFactory errorResponseFactory = new ErrorResponseFactory();

    private final FullHttpRequest request;
    private final Channel upstream;
    private final Channel downstream;
    private final boolean keepAlive;

    public void send() {
        // set request context
        upstream.attr(AttributeKeys.DOWNSTREAM_CHANNEL_KEY).set(downstream);
        upstream.attr(AttributeKeys.KEEP_ALIVED_KEY).set(keepAlive);
        upstream.attr(AttributeKeys.REQUEST_URI).set(request.uri());

        request.retain();

        upstream.writeAndFlush(request).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    request.release();
                } else {
                    FullHttpResponse errorResponse = errorResponseFactory.create();
                    downstream.writeAndFlush(errorResponse, downstream.voidPromise());

                    log.error("{} upstream channel[{}] write to backend fail",
                        request.uri(), future.channel(), future.cause());
                }
            }
        });
    }
}
