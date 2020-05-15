package com.redisplus.command;

import com.redisplus.base.bean.Connect;
import com.redisplus.base.bean.HostAndPort;
import com.redisplus.tool.CmdHlp;
import org.apache.logging.log4j.util.Strings;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class RedisClient {

    private static byte[][] getArgBytes(final String... args) throws JedisException {
        if (args == null || args.length < 1)
            throw new JedisException("请输入命令");
        final byte[][] bargs = new byte[args.length-1][];
        for (int i = 1; i < args.length; i++) {
            bargs[i - 1] = SafeEncoder.encode(args[i]);
        }
        return bargs;
    }

    public static RedisReply sendCommandLine(Connect c, final String... args) {
        try {
            final byte[][] bargs = getArgBytes(args);
            return sendCommand(c, args[0], bargs);
        } catch (Exception e) {
            return new RedisReply(e.getMessage());
        }
    }

    static class CmdContext {
        Connection conn;
        String cmd;
        byte[][] args;
        RedisReply reply;
        CmdContext(Connection conn, String cmd, byte[][] args) {
            this.conn = conn;
            this.cmd = cmd;
            this.args = args;
        }
    }

    static class CmdRun implements Runnable {
        CmdContext ctx;
        CmdRun(CmdContext ctx) {
            this.ctx = ctx;
        }
        @Override
        public void run() {
            this.ctx.reply = this.ctx.conn.sendCommand(this.ctx.cmd, this.ctx.args);
        }
    }

    private static ArrayList<RedisReply> doSendByOtherThreads(ArrayList<CmdContext> ctxs) {

        // Step1: 创建线程
        ArrayList<Thread> threads = new ArrayList<>(ctxs.size());
        for (CmdContext ctx : ctxs) {
            threads.add(new Thread(new CmdRun(ctx)));
        }

        // Step2: 执行线程
        for (Thread t : threads) {
            t.start();
        }

        // Step3: 等待线程执行结束
        for (Thread t : threads) {
            try {
                t.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Step4: 返回执行结果
        ArrayList<RedisReply> results = new ArrayList<>(ctxs.size());
        for (CmdContext ctx : ctxs) {
            if (ctx.reply == null) {
                results.add(new RedisReply("内部错误: 对端没有回应"));
            } else {
                results.add(ctx.reply);
            }
        }
        return results;
    }

    private static RedisReply sendCommandLineToAllDir(ArrayList<Connection> conns, final String... args) {
        try {
            final byte[][] bargs = getArgBytes(args);
            return sendOneCommand(conns, -1, args[0], bargs);
        } catch (Exception e) {
            return new RedisReply(e.getMessage());
        }
    }
    private static RedisReply sendOneCommand(ArrayList<Connection> conns, int connIndex, final String cmd, final byte[]... args) {

        // 为提高效率, 多线程并行做
        ArrayList<CmdContext> ctxs = new ArrayList<>(conns.size());

        if (connIndex >= 0 && connIndex < conns.size()) {
            ctxs.add(new CmdContext(conns.get(connIndex), cmd, args));
        } else {
            for (Connection conn : conns) {
                ctxs.add(new CmdContext(conn, cmd, args));
            }
        }

        ArrayList<RedisReply> results = doSendByOtherThreads(ctxs);
        if (results.isEmpty()) {
            return new RedisReply("内部错误: Server没返回任何结果");
        }

        // 任意方向出错, 返回错误
        for (RedisReply r : results) {
            if (r == null) {
                return new RedisReply("内部错误: 某方向结果为空");
            }
            if (r.replyError()) {
                return r;
            }
        }

        // 无需合并回应结果
        if (ctxs.size() == 1) {
            return results.get(0);
        }

        if (results.size() != conns.size()) {
            return new RedisReply("内部错误: 多方向时某些方向没返回结果");
        }

        // 检查结果类型是否一致
        for (int i=1; i<results.size(); i++) {
            if (! results.get(0).sameType(results.get(i)) ) {
                return new RedisReply("内部错误: 多方向时返回结果类型不一致");
            }
        }

        // 结果合并
        RedisReply mergedReply = null;
        for (RedisReply r : results) {
            if (mergedReply == null)
                mergedReply = r;
            else {
                try {
                    mergedReply.merge(r);
                } catch (Exception e) {
                    return new RedisReply(e);
                }
            }
        }

        return mergedReply;
    }

    public static RedisReply classifyKeyByDir(Connect c, List<String> keys) {
        if (c == null) {
            return new RedisReply("需先选择连接方向");
        }

        RedisReply result;
        ArrayList<Connection> conns = new ArrayList<>();
        try {
            for (HostAndPort a : HostAndPort.parse(c.getHosts())) {
                conns.add(new Connection(a.getHost(), a.getPort()));
            }

            if (conns.isEmpty()) {
                return new RedisReply("需先选择连接方向");
            }

            // 鉴权
            if (! Strings.isEmpty(c.getPass()) ) {
                result = sendCommandLineToAllDir(conns, "AUTH", c.getPass());
                if (result.replyError())
                    return result;
            }

            // 发命令获取集群节点信息
            ArrayList<RoleAndSlots> roleAndSlots = null;
            result = sendCommandLineToAllDir(conns, "CLUSTER", "NODES");
            if (result.replyError())
                return result;

            try {
                roleAndSlots = parseClusterNodes(result);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (roleAndSlots == null || roleAndSlots.size() != conns.size()) {
                return new RedisReply("内部错误: 获取ClusterNodes时错误");
            }

            int dirs = roleAndSlots.size();
            ArrayList<RedisReply> ll = new ArrayList<>(dirs);
            for (int i=0; i<dirs; i++) {
                ll.add(new RedisReply(new ArrayList<>()));
            }

            int slotNo, i, connIndex;
            byte[] bytes;
            for (String key : keys) {
                bytes = SafeEncoder.encode(key);
                slotNo = getSlot(bytes);
                connIndex = -1;
                for (i = 0; i < dirs; i++) {
                    if (roleAndSlots.get(i).inSlots(slotNo)) {
                        connIndex = i;
                        break;
                    }
                }
                if (connIndex < 0) {
                    return new RedisReply("对于Redis集群，连接方向中需要配置所有Server地址");
                }
                ll.get(connIndex).addElement(new RedisReply(bytes, false));
            }
            result = new RedisReply(ll);
        } catch (Exception e) {
            return new RedisReply("内部错误: " + e.getMessage());
        } finally {
            for (Connection conn : conns) {
                conn.close();
            }
        }

        if (result == null) {
            return new RedisReply("内部错误: 命令结果为空");
        }
        return result;
    }

    public static RedisReply sendCommand(Connect c, final String cmd, final byte[]... args) {
        if (c == null) {
            return new RedisReply("需先选择连接方向");
        }

        CmdHlp.Info cmdInfo = CmdHlp.getHlpInfo(cmd);
        if (cmdInfo == null) {
            return new RedisReply("暂不支持的命令: " + cmd);
        }

        RedisReply result;
        ArrayList<Connection> conns = new ArrayList<>();
        try {
            for (HostAndPort a : HostAndPort.parse(c.getHosts())) {
                conns.add(new Connection(a.getHost(), a.getPort()));
                if (cmdInfo.getCmdTo() == CmdHlp.CmdTo.Any)
                    break;
            }

            if (conns.isEmpty()) {
                return new RedisReply("需先选择连接方向");
            }

            // 鉴权
            if (! Strings.isEmpty(c.getPass()) ) {
                result = sendCommandLineToAllDir(conns, "AUTH", c.getPass());
                if (result.replyError())
                    return result;
            }

            // 发命令获取集群节点信息
            ArrayList<RoleAndSlots> roleAndSlots = null;
            if (!cmdInfo.getName().equals("ECHO") && !cmdInfo.getName().equals("DBSIZE")) {
                result = sendCommandLineToAllDir(conns, "CLUSTER", "NODES");
                if (result.replyError())
                    return result;
                try {
                    roleAndSlots = parseClusterNodes(result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (roleAndSlots == null || roleAndSlots.size() != conns.size()) {
                    return new RedisReply("内部错误: 获取ClusterNodes时错误");
                }

                // 集群角色检查
                boolean isMaster = false;
                boolean isSlave = false;
                for (RoleAndSlots s : roleAndSlots) {
                    if (s.isMaster) isMaster = true;
                    if (s.isSlave) isSlave = true;
                }
                if (isMaster && isSlave) {
                    return new RedisReply("后端Server角色不一致, 应该要么全是Master, 要么全是Slave");
                }
                if (!isMaster && !isSlave && conns.size() > 1) {
                    return new RedisReply("后端多个节点, 必须是集群的Master或Slave");
                }

                // 从节点只允许读
                if (isSlave) {
                    sendCommandLineToAllDir(conns, "READONLY");
                }
            }

            // 如果是单方向命令, 计算应该发往哪个方向
            int connIndex = -1;
            if (roleAndSlots != null && conns.size() > 1 && cmdInfo.getCmdTo() == CmdHlp.CmdTo.One) {
                if (args == null || args.length == 0) {
                    return new RedisReply("单方向命令不能没有Key作为参数");
                }
                int slotNo =getSlot(args[0]);
                for (int i=0; i<roleAndSlots.size(); i++) {
                    if (roleAndSlots.get(i).inSlots(slotNo)) {
                        connIndex = i;
                        break;
                    }
                }
                if (connIndex < 0) {
                    return new RedisReply("对于Redis集群，连接方向中需要配置所有Server地址");
                }
            }
            result = sendOneCommand(conns, connIndex, cmd, args);
        } catch (Exception e) {
            return new RedisReply("内部错误: " + e.getMessage());
        } finally {
            for (Connection conn : conns) {
                conn.close();
            }
        }

        if (result == null) {
            return new RedisReply("内部错误: 命令结果为空");
        }
//        result.addPeerInfo(c.getHosts());
        return result;
    }

    static class RoleAndSlots {
        boolean isMaster, isSlave;

        static class SlotStartEnd {
            int start, end;
            SlotStartEnd(int start, int end) {
                this.start = start;
                this.end = end;
            }
        }
        private ArrayList<SlotStartEnd> slots = new ArrayList<>(3);

        void addSlots(int start, int end) {
            slots.add(new SlotStartEnd(start, end));
        }
        boolean inSlots(int slot) {
            for (SlotStartEnd s : slots) {
                if (s.end >= slot && s.start <= slot)
                    return true;
            }
            return false;
        }
    }

    private final static String MySelfStr = "myself";
    private final static String MasterStr = "master";
    private final static String SlaveStr = "slave";
    private final static String ConnectedStr = "connected";
    private static ArrayList<RoleAndSlots> parseClusterNodes(RedisReply reply) throws JedisException {
        if (reply == null || ! reply.isBulkStr() )
            return new ArrayList<>(1);

        // <id> <ip:port@cport> <flags> <master> <ping-sent> <pong-recv> <config-epoch> <link-state> <slot> <slot> ... <slot>
        // 2b821240bbca69f9c89a82a2e9fb18264ac7980e pre-redis1.eichtech.top:6379@16379 myself,master - 0 1571278795000 4 connected 10922-16383
        // 10417a28256339f1a3ffdfccb643bbb8627739a0 10.81.128.249:6379@16379 myself,slave 2b821240bbca69f9c89a82a2e9fb18264ac7980e 0 0 0 connected
        String replyStr = SafeEncoder.encode(reply.getBulkStr());
        String[] lines = replyStr.split("\\r?\\n");

        ArrayList<RoleAndSlots> results = new ArrayList<>(3);
        for (int i=0; i<lines.length; i++) {
            String line_i = lines[i];
            if (Strings.isEmpty(line_i))
                continue;

            // 只关注自己
            if (! line_i.contains(MySelfStr))
                continue;

            // 获取角色: master或者slave
            RoleAndSlots rs = new RoleAndSlots();
            if (line_i.contains(MasterStr))
                rs.isMaster = true;
            if (line_i.contains(SlaveStr))
                rs.isSlave = true;
            results.add(rs);

            int linkStatePos = line_i.indexOf(ConnectedStr);
            if (linkStatePos <= 0)
                continue;
            linkStatePos += ConnectedStr.length() + 1;

            // 如果Slave没有Slot信息, 从对应的Master中获取
            if (rs.isSlave && linkStatePos >= line_i.length()
                    && i > 0 && lines[i-1] != null
                    && lines[i-1].contains(MasterStr)) {
                line_i = lines[i-1];
                linkStatePos = line_i.indexOf(ConnectedStr);
                if (linkStatePos <= 0)
                    continue;
                linkStatePos += ConnectedStr.length() + 1;
            }

            if (linkStatePos >= line_i.length())
                continue;

            String slotsInfo = line_i.substring(linkStatePos);
            String[] slotsStrArr = slotsInfo.split(" ");
            for (String s : slotsStrArr) {
                if (Strings.isEmpty(s))
                    continue;

                int islot1 = 0;
                int islot2 = 0;
                // 没分隔符时, 单个Slot值
                int sepPos = s.indexOf('-');
                try {
                    if (sepPos <= 0) {
                        islot1 = islot2 = Integer.parseInt(s);
                    } else {
                        islot1 = Integer.parseInt(s.substring(0, sepPos));
                        islot2 = Integer.parseInt(s.substring(sepPos + 1));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                rs.addSlots(islot1, islot2);
            }
        }
        return results;
    }

    private static int getSlot(byte[] bytesKey) {
        // optimization with modulo operator with power of 2
        // equivalent to getCRC16(key) % 16384
        return getCRC16(bytesKey) & (16384 - 1);
    }

    private static final int[] LOOKUP_TABLE = {0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50A5,
            0x60C6, 0x70E7, 0x8108, 0x9129, 0xA14A, 0xB16B, 0xC18C, 0xD1AD, 0xE1CE, 0xF1EF, 0x1231,
            0x0210, 0x3273, 0x2252, 0x52B5, 0x4294, 0x72F7, 0x62D6, 0x9339, 0x8318, 0xB37B, 0xA35A,
            0xD3BD, 0xC39C, 0xF3FF, 0xE3DE, 0x2462, 0x3443, 0x0420, 0x1401, 0x64E6, 0x74C7, 0x44A4,
            0x5485, 0xA56A, 0xB54B, 0x8528, 0x9509, 0xE5EE, 0xF5CF, 0xC5AC, 0xD58D, 0x3653, 0x2672,
            0x1611, 0x0630, 0x76D7, 0x66F6, 0x5695, 0x46B4, 0xB75B, 0xA77A, 0x9719, 0x8738, 0xF7DF,
            0xE7FE, 0xD79D, 0xC7BC, 0x48C4, 0x58E5, 0x6886, 0x78A7, 0x0840, 0x1861, 0x2802, 0x3823,
            0xC9CC, 0xD9ED, 0xE98E, 0xF9AF, 0x8948, 0x9969, 0xA90A, 0xB92B, 0x5AF5, 0x4AD4, 0x7AB7,
            0x6A96, 0x1A71, 0x0A50, 0x3A33, 0x2A12, 0xDBFD, 0xCBDC, 0xFBBF, 0xEB9E, 0x9B79, 0x8B58,
            0xBB3B, 0xAB1A, 0x6CA6, 0x7C87, 0x4CE4, 0x5CC5, 0x2C22, 0x3C03, 0x0C60, 0x1C41, 0xEDAE,
            0xFD8F, 0xCDEC, 0xDDCD, 0xAD2A, 0xBD0B, 0x8D68, 0x9D49, 0x7E97, 0x6EB6, 0x5ED5, 0x4EF4,
            0x3E13, 0x2E32, 0x1E51, 0x0E70, 0xFF9F, 0xEFBE, 0xDFDD, 0xCFFC, 0xBF1B, 0xAF3A, 0x9F59,
            0x8F78, 0x9188, 0x81A9, 0xB1CA, 0xA1EB, 0xD10C, 0xC12D, 0xF14E, 0xE16F, 0x1080, 0x00A1,
            0x30C2, 0x20E3, 0x5004, 0x4025, 0x7046, 0x6067, 0x83B9, 0x9398, 0xA3FB, 0xB3DA, 0xC33D,
            0xD31C, 0xE37F, 0xF35E, 0x02B1, 0x1290, 0x22F3, 0x32D2, 0x4235, 0x5214, 0x6277, 0x7256,
            0xB5EA, 0xA5CB, 0x95A8, 0x8589, 0xF56E, 0xE54F, 0xD52C, 0xC50D, 0x34E2, 0x24C3, 0x14A0,
            0x0481, 0x7466, 0x6447, 0x5424, 0x4405, 0xA7DB, 0xB7FA, 0x8799, 0x97B8, 0xE75F, 0xF77E,
            0xC71D, 0xD73C, 0x26D3, 0x36F2, 0x0691, 0x16B0, 0x6657, 0x7676, 0x4615, 0x5634, 0xD94C,
            0xC96D, 0xF90E, 0xE92F, 0x99C8, 0x89E9, 0xB98A, 0xA9AB, 0x5844, 0x4865, 0x7806, 0x6827,
            0x18C0, 0x08E1, 0x3882, 0x28A3, 0xCB7D, 0xDB5C, 0xEB3F, 0xFB1E, 0x8BF9, 0x9BD8, 0xABBB,
            0xBB9A, 0x4A75, 0x5A54, 0x6A37, 0x7A16, 0x0AF1, 0x1AD0, 0x2AB3, 0x3A92, 0xFD2E, 0xED0F,
            0xDD6C, 0xCD4D, 0xBDAA, 0xAD8B, 0x9DE8, 0x8DC9, 0x7C26, 0x6C07, 0x5C64, 0x4C45, 0x3CA2,
            0x2C83, 0x1CE0, 0x0CC1, 0xEF1F, 0xFF3E, 0xCF5D, 0xDF7C, 0xAF9B, 0xBFBA, 0x8FD9, 0x9FF8,
            0x6E17, 0x7E36, 0x4E55, 0x5E74, 0x2E93, 0x3EB2, 0x0ED1, 0x1EF0,};
    private static int getCRC16(byte[] bytesKey) {
        int crc = 0x0000;
        for (byte b : bytesKey) {
            crc = ((crc << 8) ^ LOOKUP_TABLE[((crc >>> 8) ^ (b & 0xFF)) & 0xFF]);
        }
        return crc & 0xFFFF;
    }
}
