package com.tsoft.jproxy.core;

import lombok.Data;

@Data
public class UpStreamServer {

    private static final String DEFAULT_IP = "localhost";
    private static final int DEFAULT_PORT = 80;

    private final String ip;
    private final int port;
    private final int keepAlive;

    public UpStreamServer(String host, int keepAlive) {
        this.keepAlive = keepAlive;

        int n = host.lastIndexOf(':');
        if (n >= 0) {
            // otherwise : is at the end of the string, ignore
            if (n < host.length() - 1) {
                port = Integer.parseInt(host.substring(n + 1));
            } else {
                port = DEFAULT_PORT;
            }

            ip = host.substring(0, n);
        } else {
            ip = DEFAULT_IP;
            port = DEFAULT_PORT;
        }
    }
}
