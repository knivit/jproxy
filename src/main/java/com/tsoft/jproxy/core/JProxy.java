package com.tsoft.jproxy.core;

import com.tsoft.jproxy.downstream.DownStreamHandler;
import com.tsoft.jproxy.downstream.DownStreamChannelInitializer;
import com.tsoft.jproxy.loadbalancer.LoadBalancerFactory;
import com.tsoft.jproxy.util.PlatformUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;

@Slf4j
public class JProxy {

    public void initializeAndRun(String[] args) {
        String configFile;
        if (args.length == 1) {
            configFile = args[0];
        } else {
            configFile = getClass().getResource("/jproxy.yml").getPath();
        }

        JProxyConfig config = new JProxyConfig();
        config.parse(configFile);

        LoadBalancerFactory loadBalancerFactory = new LoadBalancerFactory();
        loadBalancerFactory.init(config);

        DownStreamHandler downStreamHandler = new DownStreamHandler(config, loadBalancerFactory);

        runFromConfig(config, downStreamHandler);
    }

    private void runFromConfig(JProxyConfig config, DownStreamHandler downStreamHandler) {
        EventLoopGroup bossGroup = newEventLoopGroup(1, new DefaultThreadFactory("JProxy-Boss-Thread"));
        EventLoopGroup workerGroup = newEventLoopGroup(config.getWorkerThreads(), new DefaultThreadFactory("JProxy-Downstream-Worker-Thread"));

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup);
            b.channel(serverChannelClass());

            // connections wait for accept
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.option(ChannelOption.SO_REUSEADDR, true);
            b.childOption(ChannelOption.SO_KEEPALIVE, true);
            b.childOption(ChannelOption.TCP_NODELAY, true);
            b.childOption(ChannelOption.SO_SNDBUF, 32 * 1024);
            b.childOption(ChannelOption.SO_RCVBUF, 32 * 1024);

            // temporary settings, need more tests
            b.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024));
            b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

            // default is true, reduce thread context switching
            b.childOption(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, true);

            b.childHandler(new DownStreamChannelInitializer(config, downStreamHandler));

            Channel ch = b.bind(config.getListen())
                    .syncUninterruptibly()
                    .channel();

            log.info("bind to {} success", config.getListen());

            ch.closeFuture().syncUninterruptibly();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private EventLoopGroup newEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        if (PlatformUtil.isLinux()) {
            return new EpollEventLoopGroup(nThreads, threadFactory);
        } else if (PlatformUtil.isMac()) {
            return new KQueueEventLoopGroup(nThreads, threadFactory);
        } else {
            return new NioEventLoopGroup(nThreads, threadFactory);
        }
    }

    private Class<? extends ServerChannel> serverChannelClass() {
        if (PlatformUtil.isLinux()) {
            return EpollServerSocketChannel.class;
        } else if (PlatformUtil.isMac()) {
            return KQueueServerSocketChannel.class;
        } else {
            return NioServerSocketChannel.class;
        }
    }
}
