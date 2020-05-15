package com.eichong.base.bean;

import org.apache.logging.log4j.util.Strings;

import java.util.LinkedList;
import java.util.List;

public class HostAndPort {
    private String host;
    private int port;

    public String getHost() {
        return host;
    }
    public int getPort() {
        return port;
    }

    private HostAndPort() {
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }


    public static List<HostAndPort> parse(String hosts) {
        List<HostAndPort> l = new LinkedList<>();
        if (Strings.isEmpty(hosts))
            return l;
        String[] re = hosts.split(" |,");
        for (String i : re) {
            if (Strings.isEmpty(i))
                continue;
            HostAndPort.parse_i(i, l);
        }
        return l;
    }

    private static void parse_i(String host, List<HostAndPort> list) {
        if (Strings.isEmpty(host))
            return;

        int i = host.indexOf(':');
        if (i <= 0) {
            HostAndPort re = new HostAndPort();
            re.host = host;
            re.port = 6379;
            list.add(re);
            return;
        }

        HostAndPort re = new HostAndPort();
        re.host = host.substring(0, i);
        re.port = Integer.parseInt(host.substring(i+1));
        list.add(re);
    }
}