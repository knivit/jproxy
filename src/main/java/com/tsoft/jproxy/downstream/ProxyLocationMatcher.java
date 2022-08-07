package com.tsoft.jproxy.downstream;

import com.tsoft.jproxy.core.JProxyConfig;
import com.tsoft.jproxy.conf.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProxyLocationMatcher {

    private static final Comparator<Location> LONGEST_FIRST = (a, b) -> {
        int al = (a.getPath() == null) ? 0 : a.getPath().length();
        int bl = (b.getPath() == null) ? 0 : b.getPath().length();
        return -Integer.compare(al, bl);
    };

    public Location getProxyLocation(JProxyConfig config, String serverName, String uri) {
        List<Location> locations = config.getServers().get(serverName);
        if (locations == null) {
            return null;
        }

        List<Location> sorted = new ArrayList<>(locations);
        sorted.sort(LONGEST_FIRST);

        for (Location location : sorted) {
            if (uri.startsWith(location.getPath())) {
                return location;
            }
        }

        return null;
    }
}
