package com.tsoft.jproxy.loadbalancer;

import com.tsoft.jproxy.core.UpStreamServer;

public interface LoadBalancer {

    UpStreamServer next();
}
