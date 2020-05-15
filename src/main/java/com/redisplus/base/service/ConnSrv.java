package com.redisplus.base.service;

import com.redisplus.base.bean.Connect;
import com.redisplus.base.bean.Setting;
import com.redisplus.tool.ConfIO;
import org.apache.logging.log4j.util.Strings;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class ConnSrv {

    private static ConfIO connConf_ = new ConfIO();
    private static ConfIO settingConf_ = new ConfIO();

    static class ConnComparable implements Comparable<Connect> {
        private String name;
        ConnComparable(String name) {
            this.name = name;
        }
        @Override
        public int compareTo(Connect c) {
            return name.compareTo(c.getName());
        }
    }
    static class ConnComparator implements Comparator<Connect> {
        @Override
        public int compare(Connect o1, Connect o2) {
            if (o1 == null)
                return o2==null?0:-1;
            if (o2 == null)
                return 1;
            return o1.getName().compareTo(o2.getName());
        }
    }
    static class SettingComparable implements Comparable<Setting> {
        private String key;
        SettingComparable(String key) {
            this.key = key;
        }
        @Override
        public int compareTo(Setting s) {
            return key.compareTo(s.getKey());
        }
    }

    static {
        connConf_.load("connect.json");
        settingConf_.load("setting.json");
    }

    public static Connect getConnectByName(String name) {
        if (Strings.isEmpty(name))
            return null;

        List<Connect> conns = connConf_.query(Connect.class);
        if (conns == null || conns.isEmpty())
            return null;

        for (Connect c : conns) {
            if (name.equals(c.getName()))
                return c;
        }
        return null;
    }

    private static boolean firstTime_ = true;
    public static List<Connect> queryConnect() {
        List<Connect> re = connConf_.query(Connect.class);

        if (firstTime_) {
            if (re == null || re.isEmpty()) {
                Connect conn = new Connect();
                conn.setName("测试");
                conn.setHosts("10.9.2.88,10.9.3.151,10.9.3.152");
                conn.setPass("0987654321rfvujmtgbyhn");
                updateConnect(conn);

                conn = new Connect();
                conn.setName("预发");
                conn.setHosts("47.99.215.129,47.99.215.129:7001,47.99.215.129:7002");
                conn.setPass("acwl20180519rfvujmtgbyhn");
                updateConnect(conn);

                re = connConf_.query(Connect.class);
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                System.out.println(df.format(new Date()) + " init size:" + re.size());
            }
            firstTime_ = false;
        }

        re.sort(new ConnComparator());
        return re;
    }

    public static int updateConnect(Connect connect) {
        if (connect == null || Strings.isEmpty(connect.getName())) {
            return 0;
        }
        return connConf_.update(new ConnComparable(connect.getName()), connect, Connect.class);
    }

    public static int deleteConnectByName(String name) {
        if (Strings.isEmpty(name)) {
            return 0;
        }
        return connConf_.update(new ConnComparable(name), null, Connect.class);
    }

    final static String CurConn = "CurConn";
    public static Connect getCurrentConn() {
        List<Setting> settings = settingConf_.query(Setting.class);
        if (settings == null || settings.isEmpty())
            return null;
        String connName = null;
        for (Setting s : settings) {
            if (CurConn.equals(s.getKey())) {
                connName = s.getVal();
                break;
            }
        }
        if (connName == null)
            return null;
        return getConnectByName(connName);
    }

    public static void saveCurrentConn(Connect connect) {
        if (connect == null || Strings.isEmpty(connect.getName())) {
            return;
        }
        Setting s = new Setting();
        s.setKey(CurConn);
        s.setVal(connect.getName());
        settingConf_.update(new SettingComparable(CurConn), s, Setting.class);
    }

}
