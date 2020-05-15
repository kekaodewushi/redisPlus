package com.eichong.base.controller;

import com.alibaba.fastjson.JSON;
import com.eichong.base.bean.Connect;
import com.eichong.base.bean.HostAndPort;
import com.eichong.base.service.ConnSrv;
import com.eichong.base.service.RedisSrv;
import com.eichong.command.JedisException;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.eichong.base.bean.ResultInfo.*;

@Component
public class ConnectController {

    /**
     * 查询连接列表
     */
    @SuppressWarnings("unused")
    public String queryConnect() {
        return getOkByJson(JSON.toJSONString(ConnSrv.queryConnect()));
    }

    /**
     * 新增连接数据
     */
    @SuppressWarnings("unused")
    public String insertConnect(String json) {
        try {
            Connect conn = JSON.parseObject(json, Connect.class);
            checkConnectAddress(conn);
            int insFlag = ConnSrv.updateConnect(conn);
            if (insFlag == 1) {
                return getOkByJson("新增连接成功");
            } else {
                return getErrByJson("新增连接失败");
            }
        } catch (Exception e) {
            return exception(e);
        }
    }

    private void checkConnectAddress(Connect conn) throws JedisException {
        if (Strings.isEmpty(conn.getHosts()))
            throw new JedisException("RedisServer地址为空");
        for (HostAndPort a : HostAndPort.parse(conn.getHosts())) {
            try {
                com.eichong.command.Connection.checkAdressValid(a.getHost(), a.getPort());
            } catch (Exception e) {
                e.printStackTrace();
                throw new JedisException("RedisServer地址格式错误，多个地址以','隔开");
            }
        }
    }

    /**
     * 更新连接数据
     */
    @SuppressWarnings("unused")
    public String updateConnect(String oldConnName, String json) {
        try {
            Connect conn = JSON.parseObject(json, Connect.class);
            checkConnectAddress(conn);
            int updFlag = ConnSrv.updateConnect(conn);
            if (updFlag == 1) {
                if (oldConnName != null && !oldConnName.equals(conn.getName()))
                    ConnSrv.deleteConnectByName(oldConnName);
                return getOkByJson("修改连接成功");
            } else {
                return getErrByJson("修改连接失败");
            }
        } catch (Exception e) {
            return exception(e);
        }
    }

    /**
     * 删除连接数据
     */
    @SuppressWarnings("unused")
    public String deleteConnect(String json) {
        try {
            boolean deleted = false;
            List<Connect> l = JSON.parseArray(json, Connect.class);
            for (Connect c : l) {
                if (ConnSrv.deleteConnectByName(c.getName()) == 1)
                    deleted = true;
            }
            if (deleted) {
                return getOkByJson("删除连接成功");
            } else {
                return getErrByJson("删除连接失败");
            }
        } catch (Exception e) {
            return exception(e);
        }
    }

    /**
     * 测试连接状态
     */
    @SuppressWarnings("unused")
    public String checkConnect(String data, boolean asCurrentConn) {
        Connect connect = null;
        try {
            connect = JSON.parseObject(data, Connect.class);
        } catch (Exception e) {
        }
        try {
            if (connect == null) {
                connect = ConnSrv.getConnectByName(data);
            }
            if (connect == null) {
                return getErrByJson("请勾选或双击某行, 选择连接方向");
            }
            checkConnectAddress(connect);
            if (RedisSrv.echo(connect)) {
                if (asCurrentConn)
                    ConnSrv.saveCurrentConn(connect);
                return getOkByJson("连接成功");
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg.contains("UnknownHostKey")) {
                return getErrByJson("未知的主机");
            }
            if (msg.contains("connect failed")) {
                return getErrByJson("网络不可达");
            }
            if (msg.contains("invalid password")) {
                return getErrByJson("密码不正确");
            }
            if (msg.contains("connect timed out")) {
                return getErrByJson("请求服务超时");
            }
            if (msg.contains("Connection refused")) {
                return getErrByJson("服务拒绝连接");
            }
            if (msg.contains("Authentication required")) {
                return getErrByJson("密码不能为空");
            }
            return getErrByJson(msg);
        }
        return getErrByJson("服务不可用");
    }
}
