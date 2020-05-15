package com.eichong.base.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.eichong.base.bean.*;
import com.eichong.command.*;
import com.eichong.tool.UidUtil;
import org.apache.logging.log4j.util.Strings;

import java.util.*;

public final class RedisSrv {

    private final static String VPathPattern = ":vpath-for-page:";
    private static Connect conn() throws JedisException {
        Connect conn = ConnSrv.getCurrentConn();
        if (conn == null) {
            throw new JedisException("需先选择连接方向");
        }
        if (Strings.isEmpty(conn.getHosts())) {
            throw new JedisException("当前连接Server地址为空");
        }
        return conn;
    }

    private static String keyType(String key) throws JedisException {
        RedisReply reply = RedisClient.sendCommandLine(conn(), "TYPE", key);
        if (reply.replyError()) {
            throw new JedisException(reply.getStatus());
        }
        String re = reply.getStatus();
        if (Strings.isEmpty(re)) {
            throw new JedisException("获取KeyType结果为空, Key: "+key);
        }
        if ("none".equals(re)) {
            throw new JedisException("KEY不存在");
        }
        if (!"string,list,set,zset,hash,stream,".contains(re+",")) {
            throw new JedisException("获取KeyType错误, Key: "+key + ", 结果: " + re);
        }
        return re;
    }

    public static boolean exists(String key) {
        try {
            RedisReply reply = RedisClient.sendCommandLine(conn(), "EXISTS", key);
            if (!reply.replyError() && reply.getInteger() == 1) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * 重命名key
     */
    public static void renameKey(String oldKey, String newKey) throws JedisException {
        String keyType = keyType(oldKey);
        RedisReply reply;
        if (keyType.equals("string")) {
            reply = RedisClient.sendCommandLine(conn(), "GET", oldKey);
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            reply = RedisClient.sendCommand(conn(), "SET",
                    SafeEncoder.encode(newKey),
                    reply.getBulkStr());
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            RedisClient.sendCommandLine(conn(), "DEL", oldKey);
            return;
        }

        if (keyType.equals("list")) {
            reply = RedisClient.sendCommandLine(conn(), "LRANGE", oldKey, "0", "-1");
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            byte[][] bargs = replyToArgs(newKey, reply);
            RedisClient.sendCommand(conn(), "RPUSH", bargs);
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            RedisClient.sendCommandLine(conn(), "DEL", oldKey);
            return;
        }

        if (keyType.equals("set")) {
            reply = RedisClient.sendCommandLine(conn(), "SMEMBERS", oldKey);
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            byte[][] bargs = replyToArgs(newKey, reply);
            RedisClient.sendCommand(conn(), "SADD", bargs);
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            RedisClient.sendCommandLine(conn(), "DEL", oldKey);
            return;
        }

        if (keyType.equals("zset")) {
            reply = RedisClient.sendCommandLine(conn(), "ZRANGE", oldKey, "0", "-1", "WITHSCORES");
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            byte[][] bargs = replyToArgs(newKey, reply);
            // ZADD要求SCORE在前
            if ((bargs.length & 1) != 1) {
                throw new JedisException("ZRANGE结果错误");
            }
            byte[] tmpBytes;
            // 将参数位置交换一下，ZADD要求Score在前，但是ZRANGE返回的确是Member在前
            for (int l=bargs.length-1, i=1; i<l; i+=2) {
                tmpBytes = bargs[i];
                bargs[i] = bargs[i+1];
                bargs[i+1] = tmpBytes;
            }
            if (bargs.length > 1) {
                RedisClient.sendCommand(conn(), "ZADD", bargs);
                if (reply.replyError()) {
                    throw new JedisException(reply.getStatus());
                }
            }
            RedisClient.sendCommandLine(conn(), "DEL", oldKey);
            return;
        }

        if (keyType.equals("hash")) {
            reply = RedisClient.sendCommandLine(conn(), "HGETALL", oldKey);
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            byte[][] bargs = replyToArgs(newKey, reply);
            RedisClient.sendCommand(conn(), "HMSET", bargs);
            if (reply.replyError()) {
                throw new JedisException(reply.getStatus());
            }
            RedisClient.sendCommandLine(conn(), "DEL", oldKey);
            return;
        }

        throw new JedisException("暂不支持此类型的值重命令, Key: "+oldKey + ", keyType: " + keyType);
    }

    private static byte[][] replyToArgs(String newKey, RedisReply reply) throws JedisException {
        List<RedisReply> l = reply.getArray();
        byte[][] bargs = new byte[l.size() + 1][];
        int pos = 0;
        bargs[pos++] = SafeEncoder.encode(newKey);
        for (RedisReply r : l) {
            bargs[pos++] = r.getBulkStr();
        }
        return bargs;
    }

    public static void sendCommandLine(final String... args) throws JedisException {
        RedisReply reply = RedisClient.sendCommandLine(conn(), args);
        if (reply.replyError()) {
            throw new JedisException(reply.getStatus());
        }
    }

    /**
     * 获取库的key数量
     */
    private static long dbSize() throws JedisException {
        RedisReply reply = RedisClient.sendCommandLine(conn(), "DBSIZE");
        if (reply.replyError()) {
            throw new JedisException(reply.getStatus());
        }
        return reply.getInteger();
    }

    public static boolean echo(Connect conn) throws JedisException {
        if (conn == null)
            conn = conn();
        RedisReply reply = RedisClient.sendCommandLine(conn, "ECHO", "Test");
        if (reply.replyError()) {
            throw new JedisException(reply.getStatus());
        }
        String res = SafeEncoder.encode(reply.getBulkStr());
        String[] re = res.split("\\r?\\n");
        for (String i : re) {
            if (Strings.isEmpty(i))
                return false;
            if(!i.equals("Test"))
                return false;
        }
        return true;
    }

    public static class PathAndKeys {
        public String path;
        public Long keys;
    }
    public static PathAndKeys searchPath(String pattern) throws JedisException {
        PathAndKeys re = new PathAndKeys();
        if (Strings.isEmpty(pattern) || pattern.equals("*")) {
            re.path = "*";
            re.keys = dbSize();
            return re;
        }
        RedisReply reply = RedisClient.sendCommandLine(conn(), "SEARCHPATH", pattern);
        if (reply.replyError()) {
            throw new JedisException(reply.getStatus());
        }
        for (RedisReply r : reply.getArray()) {
            if (re.path == null)
                re.path = SafeEncoder.encode(r.getBulkStr());
            else if (re.keys == null)
                re.keys = Long.parseLong(SafeEncoder.encode(r.getBulkStr()));
            else
                break;
        }
        if (re.path == null)
            re.path = "*";
        if (re.keys == null)
            re.keys = (long)0;
        return re;
    }

    private static List<String> keysInPath(String addKey, String path) throws JedisException {
        RedisReply  reply = RedisClient.sendCommandLine(conn(), "KEYSINPATH", path);
        if (reply.replyError()) {
            throw new JedisException(reply.getStatus());
        }
        List<String> re = new ArrayList<>(reply.getArray().size() + (addKey!=null?1:0));
        if (addKey != null)
            re.add(addKey);
        for (RedisReply r : reply.getArray()) {
            re.add(SafeEncoder.encode(r.getBulkStr()));
        }
        return re;
    }

    public static List<ZTreeBean> treeData(String pid, String path, String pattern) throws JedisException {
        if (VPathPattern.equals(pattern)) {
            throw new JedisException("虚拟目录仅用于分页显示，请刷新上一级目录");
        }

        String addKey = null;
        boolean filterByPattern = false;
        if (pid.equals("root") && !pattern.equals("*"))
            filterByPattern = true;

        if (filterByPattern
            && pattern.length() == path.length()
            && exists(pattern))
        {
            addKey = pattern;
        }

        // 查询到所有符合条件的Key列表
        List<String> keyList = keysInPath(addKey, path);
        if (keyList == null || keyList.isEmpty()) {
            return new ArrayList<>();
        }

        // 排序, 以便在树上好找
        Collections.sort(keyList);

        // Cache所有目录节点
        HashMap<String, ZTreeBean> pathNodes = new HashMap<>();
        LinkedList<ZTreeBean> children = new LinkedList<>();

        /*  下面只需处理Key的rootPathLen之后的部分,
            因为是要添加到父节点的下面(pid和path是其标识),
            不一定是添加到根目录下 */

        int rootPathLen = 0;
        if (!path.equals("*"))
            rootPathLen = path.length() + 1; // +1: 包括 ":"

        String pathFullName;
        String parentId_i;
        int parentPathLen_i, pos;
        ZTreeBean node_i;
        LinkedList<ZTreeBean> children_i;
        for (String key : keyList) {
            parentId_i = pid;
            parentPathLen_i = rootPathLen;
            children_i = children;

            // 严格按照Pattern过滤
            if (filterByPattern && !key.startsWith(pattern)) {
                continue;
            }

            // 先创建各级父目录
            for (pos = key.indexOf(':', parentPathLen_i);
                 pos > 0;
                 pos = key.indexOf(':', parentPathLen_i))
            {
                pathFullName = key.substring(0, pos);

                // 先从Cache中查找, 避免重复创建
                node_i = pathNodes.get(pathFullName);
                if (node_i == null) {
                    node_i = new ZTreeBean(UidUtil.getUID().toString(),
                            parentId_i,
                            key.substring(parentPathLen_i, pos), // 显示在树上时, Path只显示本级名称
                            pathFullName,
                            true,
                            new LinkedList<>());
                    // 将本级目录添加到父目录, 作为其孩子节点
                    children_i.add(node_i);
                    pathNodes.put(pathFullName, node_i);
                }

                // 将本节点作为父节点, 继续深入
                parentId_i = node_i.getPId();
                parentPathLen_i = pos;
                parentPathLen_i++; // 包括 ":"
                children_i = node_i.getChildren();
            }

            // 将Key添加到父目录, 作为其孩子节点
            children_i.add(new ZTreeBean(UidUtil.getUID().toString(),
                    parentId_i,
                    key, // Key显示全名
                    key,
                    false));
        }

        // 分页，否则树很久都打不开
        int itemsInPage = 1000;
        if (children.size() > itemsInPage) {
            LinkedList<ZTreeBean> toBeDivided = children;
            children = new LinkedList<ZTreeBean>();
            ZTreeBean vpath = null;
            for (ZTreeBean b : toBeDivided) {
                if (vpath == null) {
                    vpath = new ZTreeBean(UidUtil.getUID().toString(),
                            pid,
                            b.getName() + "...", // 显示在树上时, Path只显示本级名称
                            VPathPattern,
                            true,
                            new LinkedList<>());
                    children.add(vpath);
                }
                vpath.getChildren().add(b);
                if (vpath.getChildren().size() >= itemsInPage)
                    vpath = null;
            }
        }
        return children;
    }

    public static void delFilteredTree(String path, String pattern) throws JedisException {
        if (pattern.equals("*")) {
            throw new JedisException("清空全部数据是高危操作，建议找运维重命名aof文件再重启Server");
        }

        String addKey = null;
        if (exists(pattern)) {
            addKey = pattern;
        }

        // 查询到所有符合条件的Key列表
        List<String> keyList = keysInPath(addKey, path);
        if (keyList == null || keyList.isEmpty()) {
            return;
        }

        int rootPathLen = path.length() + 1; // +1: 包括 ":"
        int pos;
        Set<String> subPathSet = new HashSet<>();
        List<String> childrenKeyList = new ArrayList<>(keyList.size());
        for (String key : keyList) {

            // 严格按照Pattern过滤
            if (!key.startsWith(pattern)) {
                continue;
            }

            pos = key.indexOf(':', rootPathLen);
            if (pos > 0) {
                subPathSet.add(key.substring(0, pos));
            } else {
                childrenKeyList.add(key);
            }
        }

        // 将Key按方向归类，相同方向的成批发送
        RedisReply reply = RedisClient.classifyKeyByDir(conn(), childrenKeyList);
        if (reply.replyError()) {
            throw new JedisException(reply.getStatus());
        }

        // 先清空各符合条件的子目录
        for (String subPath : subPathSet) {
            RedisClient.sendCommandLine(conn(), "DELPATH", subPath);
        }

        // 再成批删掉其它Key
        for (RedisReply i : reply.getArray()) {
            List<RedisReply> l = i.getArray();
            if (l.isEmpty())
                continue;
            byte[][] args = new byte[l.size()][];
            pos = 0;
            for (RedisReply j : l) {
                args[pos++] = j.getBulkStr();
            }
            RedisClient.sendCommand(conn(), "DEL", args);
        }
    }

    /**
     * 获取Redis Key信息
     */
    public static KeyBean getKeyInfo(String key) throws JedisException {
        String type = keyType(key);
        RedisReply ttlReply = RedisClient.sendCommandLine(conn(), "TTL", key);
        if (ttlReply.replyError()) {
            throw new JedisException(ttlReply.getStatus());
        }

        KeyBean keyBean = new KeyBean();
        keyBean.setKey(key);
        keyBean.setType(type);
        keyBean.setTtl(ttlReply.getInteger());

        RedisReply reply;
        List<String> list = new ArrayList<>();
        String key_i, value_i;
        byte[] bulkStr;
        long size = 0;
        StringBuilder strBuf = new StringBuilder();
        int i;
        switch (type) {
            //set (集合)
            case "set":
            case "list":
                if (type.equals("set")) {
                    reply = RedisClient.sendCommandLine(conn(), "SMEMBERS", key);
                } else {
                    reply = RedisClient.sendCommandLine(conn(), "LRANGE", key, "0", "-1");
                }
                if (reply.replyError()) {
                    throw new JedisException(reply.getStatus());
                }

                // 保存到list并对List做排序
                size = parseReplyStrArray(reply, list);
                // list先不改变顺序，因为修改操作是位置强相关的
                if (type.equals("set")) Collections.sort(list);

                // 然后转为OneFieldBean，方便表格显示和处理
                List<OneFieldBean> ofBeanList = new ArrayList<>(list.size());
                OneFieldBean ofbean;
                for (String item : list) {
                    ofbean = new OneFieldBean();
                    ofbean.setField(item);
                    ofBeanList.add(ofbean);
                }
                keyBean.setText(strListToText(list));
                keyBean.setJson(JSON.toJSONString(ofBeanList));
                break;
            case "zset":
                // 用ZRANGEBYSCORE时，获取不了全部(score为负值的元素)
                reply = RedisClient.sendCommandLine(conn(),"ZRANGE", key, "0", "-1", "WITHSCORES");
                if (reply.replyError()) {
                    throw new JedisException(reply.getStatus());
                }
                if ((reply.getArray().size() & 1) == 1) {
                    throw new JedisException("ZREVRANGE回应项数量不是偶数");
                }

                // 先作为ZsetTuple列表，便于排序
                List<ZsetTuple> zsetList = new ArrayList<>();
                Iterator<RedisReply> iterator = reply.getArray().iterator();
                while (iterator.hasNext()) {
                    bulkStr = iterator.next().getBulkStr();
                    size += bulkStr.length;
                    key_i = SafeEncoder.encode(bulkStr);
                    bulkStr = iterator.next().getBulkStr();
                    size += bulkStr.length;
                    value_i = SafeEncoder.encode(bulkStr);
                    zsetList.add(new ZsetTuple(key_i, value_i));
                }
                Collections.sort(zsetList);

                // 先作为ZsetTuple列表，便于排序
                i = 1;
                List<ZsetBean> zbeanList = new ArrayList<>(zsetList.size());
                ZsetBean zbean;
                for (ZsetTuple t : zsetList) {
                    strBuf.append(i++);
                    strBuf.append(") ");
                    strBuf.append(t.getElement());
                    strBuf.append("<br>");
                    strBuf.append(i++);
                    strBuf.append(") ");
                    strBuf.append(t.getStrScore());
                    strBuf.append("<br>");

                    zbean = new ZsetBean();
                    zbean.setMember(t.getElement());
                    zbean.setScore(t.getScore());
                    zbeanList.add(zbean);
                }
                keyBean.setText(strBuf.toString());
                keyBean.setJson(JSON.toJSONString(zbeanList));
                break;
            case "hash":
                reply = RedisClient.sendCommandLine(conn(), "HGETALL", key);
                if (reply.replyError()) {
                    throw new JedisException(reply.getStatus());
                }
                if ((reply.getArray().size() & 1) == 1) {
                    throw new JedisException("HGETALL回应项数量不是偶数");
                }

                // 对Key排序
                TreeMap<String, String> map = new TreeMap<>();
                iterator = reply.getArray().iterator();
                while (iterator.hasNext()) {
                    bulkStr = iterator.next().getBulkStr();
                    size += bulkStr.length;
                    key_i = SafeEncoder.encode(bulkStr);
                    bulkStr = iterator.next().getBulkStr();
                    size += bulkStr.length;
                    value_i = SafeEncoder.encode(bulkStr);
                    map.put(key_i, value_i);
                }

                i = 1;
                List<HsetBean> hbeanList = new ArrayList<>(map.size());
                HsetBean hbean;
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    strBuf.append(i++);
                    strBuf.append(") ");
                    strBuf.append(entry.getKey());
                    strBuf.append("<br>");
                    strBuf.append(i++);
                    strBuf.append(") ");
                    strBuf.append(entry.getValue());
                    strBuf.append("<br>");

                    hbean = new HsetBean();
                    hbean.setField(entry.getKey());
                    hbean.setValue(entry.getValue());
                    hbeanList.add(hbean);
                }
                keyBean.setText(strBuf.toString());
                keyBean.setJson(JSON.toJSONString(hbeanList));
                break;
            case "string":
                reply = RedisClient.sendCommandLine(conn(), "GET", key);
                if (reply.replyError()) {
                    throw new JedisException(reply.getStatus());
                }
                size = reply.getBulkStr().length;
                value_i = SafeEncoder.encode(reply.getBulkStr());
                keyBean.setText(value_i);
                if (isJSONValid(value_i)) {
                    keyBean.setJson(value_i);
                }
                break;
        }
        keyBean.setSize(size);
        return keyBean;
    }

    private static long parseReplyStrArray(RedisReply reply, List<String> list) throws JedisException {
        if (reply.replyError()) {
            throw new JedisException(reply.getStatus());
        }
        long size = 0;
        for (RedisReply r : reply.getArray()) {
            size += r.getBulkStr().length;
            String str = SafeEncoder.encode(r.getBulkStr());
            list.add(str);
        }
        return size;
    }

    private static String strListToText(List<String> list) {
        StringBuilder strBuf = new StringBuilder();
        int i = 1;
        for (String str : list) {
            strBuf.append(i++);
            strBuf.append(") ");
            strBuf.append(str);
            strBuf.append("<br>");
        }
        return strBuf.toString();
    }

    private static boolean isJSONValid(String test) {
        try {
            JSONObject.parseObject(test);
        } catch (Exception ex) {
            try {
                JSONObject.parseArray(test);
            } catch (Exception ex1) {
                return false;
            }
        }
        return true;
    }

    static class ZsetTuple implements Comparable<ZsetTuple> {
        private String element,strScore;
        private Double score;

        ZsetTuple(String element, String score) throws JedisException {
            super();
            if (element == null || score == null)
                throw new JedisException("element == null || score == null");
            this.element = element;
            this.strScore = score;
            this.score = Double.valueOf(score);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            ZsetTuple other = (ZsetTuple) obj;
            return element.equals(other.element);
        }

        @Override
        public int compareTo(ZsetTuple other) {
            if (this.score == other.getScore() || element.equals(other.element)) return 0;
            else return this.score < other.getScore() ? -1 : 1;
        }

        String getElement() {
            return element;
        }
        String getStrScore() {
            return strScore;
        }
        double getScore() {
            return score;
        }
    }

}
