package com.tsoft.jproxy.downstream;

import com.tsoft.jproxy.conf.Location;
import com.tsoft.jproxy.core.JProxyConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyLocationMatcherTest {

    private static final ProxyLocationMatcher matcher = new ProxyLocationMatcher();

    @Test
    void get_proxy_location() {
        JProxyConfig cfg = new JProxyConfig()
            .setServers(Collections.emptyMap());

        assertThat(matcher.getProxyLocation(cfg, "test", "/"))
            .isNotNull()
            .extracting(Location::getPath)
            .isEqualTo("/");
    }
}