package com.tsoft.jproxy.core;

import io.netty.channel.Channel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Connection {

	private final UpStreamServer upStreamServer;
	private final Channel channel;

}
