package com.tsoft.jproxy.upstream;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import com.tsoft.jproxy.core.UpStreamServer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UpStreamChannelInitializer extends ChannelInitializer<Channel>{
	
	private final UpStreamServer upStreamServer;
	private final String proxyPass;

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		pipeline.addLast(new HttpClientCodec());
		pipeline.addLast(new HttpObjectAggregator(512 * 1024));
		pipeline.addLast(new UpStreamHandler(upStreamServer, proxyPass));
	}
}
