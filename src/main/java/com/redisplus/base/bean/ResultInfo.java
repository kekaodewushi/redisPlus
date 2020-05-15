package com.redisplus.base.bean;

import com.alibaba.fastjson.JSON;
//import com.redisplus.tool.LogUtil;
import lombok.Data;

@Data
public class ResultInfo {

    private String msgs;

    private Object data;

    private Integer code;

    public ResultInfo() {

    }

    public ResultInfo(Integer code, Object data, String msgs) {
        this.code = code;
        this.data = data;
        this.msgs = msgs;
    }

    public static String getOkByJson(String msgs) {
        return JSON.toJSONString(new ResultInfo(200, null, msgs));
    }

    public static String getOkByJson(Object data) {
        return JSON.toJSONString(new ResultInfo(200, data, ""));
    }

    public static String getErrByJson(String msgs) {
        return JSON.toJSONString(new ResultInfo(300, null, msgs));
    }

    public static String exception(Exception e) {
        e.printStackTrace();
        return JSON.toJSONString(new ResultInfo(500, null, e.getMessage()));
    }

    public static String exceptionByMsgs(String msgs) {
        return JSON.toJSONString(new ResultInfo(500, null, msgs));
    }

    public static String disconnect() {
        return JSON.toJSONString(new ResultInfo(500, null, "服务连接已断开"));
    }


}
