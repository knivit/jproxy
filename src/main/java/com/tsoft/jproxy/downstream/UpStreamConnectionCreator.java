package com.tsoft.jproxy.downstream;

import com.tsoft.jproxy.core.UpStreamServer;
import com.tsoft.jproxy.response.ErrorResponseFactory;
import com.tsoft.jproxy.upstream.UpStreamChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UpStreamConnectionCreator {

    private final ErrorResponseFactory errorResponseFactory = new ErrorResponseFactory();

    private final Channel downstream;
    private final UpStreamServer upStreamServer;
    private final String proxyPass;
    private final FullHttpRequest request;
    private final boolean keepAlive;

    public void connect() {
        Bootstrap b = new Bootstrap();
        b.group(downstream.eventLoop());
        b.channel(downstream.getClass());

        b.option(ChannelOption.TCP_NODELAY, true);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        // default is pooled direct
        // ByteBuf(io.netty.util.internal.PlatformDependent.DIRECT_BUFFER_PREFERRED)
        b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        // 32kb(for massive long connections, See
        // http://www.infoq.com/cn/articles/netty-million-level-push-service-design-points)
        // 64kb(RocketMq remoting default value)
        b.option(ChannelOption.SO_SNDBUF, 32 * 1024);
        b.option(ChannelOption.SO_RCVBUF, 32 * 1024);
        // temporary settings, need more tests
        b.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024));
        // default is true, reduce thread context switching
        b.option(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, true);

        b.handler(new UpStreamChannelInitializer(upStreamServer, proxyPass));

        ChannelFuture connectFuture = b.connect(upStreamServer.getIp(), upStreamServer.getPort());

        connectFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    UpStreamRequestSender requestSender = new UpStreamRequestSender(request, future.channel(), downstream, keepAlive);
                    requestSender.send();
                } else {
                    request.release();

                    FullHttpResponse errorResponse = errorResponseFactory.create();
                    downstream.writeAndFlush(errorResponse, downstream.voidPromise());
                }
            }
        });
    }
}
