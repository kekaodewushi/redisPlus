package com.redisplus.base.controller;

import com.redisplus.base.bean.ResultInfo;
import com.redisplus.core.desktop.Desktop;
import com.redisplus.tool.UidUtil;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import static com.redisplus.tool.ItemUtil.*;

@Component
public class JsCallJava {

    private static ConcurrentHashMap<String, String> resultMap_ = new ConcurrentHashMap<>();

    private ConnectController conn_;
    private DataSinglesController data_;

    private static Method[] connMethods_ = null;
    private static Method[] dataMethods_ = null;

    private final static String WaitingForResponse = "Waiting for redis server's response";
    public void init(ConnectController conn, DataSinglesController data) {
        conn_ = conn;
        data_ = data;
        if (connMethods_ == null)
            connMethods_ = conn.getClass().getMethods();
        if (dataMethods_ == null)
            dataMethods_ = data.getClass().getMethods();
    }

    @SuppressWarnings("unused")
    public String call2(String controller, String func) {
        return call_i(controller, func);
    }

    @SuppressWarnings("unused")
    public String call3(String controller, String func, Object arg1) {
        return call_i(controller, func, arg1);
    }

    @SuppressWarnings("unused")
    public String call4(String controller, String func, Object arg1, Object arg2) {
        return call_i(controller, func, arg1, arg2);
    }

    @SuppressWarnings("unused")
    public String call5(String controller, String func, Object arg1, Object arg2, Object arg3) {
        return call_i(controller, func, arg1, arg2, arg3);
    }

    @SuppressWarnings("unused")
    public String call6(String controller, String func, Object arg1, Object arg2, Object arg3, Object arg4) {
        return call_i(controller, func, arg1, arg2, arg3, arg4);
    }

    @SuppressWarnings("unused")
    public String call7(String controller, String func, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        return call_i(controller, func, arg1, arg2, arg3, arg4, arg5);
    }

    @SuppressWarnings("unused")
    public String call8(String controller, String func, Object arg1, Object arg2,
                       Object arg3, Object arg4, Object arg5, Object arg6) {
        return call_i(controller, func, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    @SuppressWarnings("unused")
    public String call9(String controller, String func, Object arg1, Object arg2,
                       Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        return call_i(controller, func, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    @SuppressWarnings("unused")
    public String call10(String controller, String func, Object arg1, Object arg2,
                       Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        return call_i(controller, func, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    private String call_i(String controller, String func, Object... args) {
        if (resultMap_.size() >= 5) {
            return ResultInfo.exceptionByMsgs("系统忙, 稍后再试");
        }

        if (Strings.isEmpty(controller) || Strings.isEmpty(func)) {
            return ResultInfo.exceptionByMsgs("内部错误: 调用Controller时类名或方法名为空");
        }

        Method m = null;
        Object ins = null;
        if (controller.contains("data") || controller.contains("Data")) {
            ins = data_;
            for (Method i : dataMethods_) {
                if (i.getName().equals(func)) {
                    m = i;
                    break;
                }
            }
        } else if (controller.contains("connect") || controller.contains("Connect")) {
            ins = conn_;
            for (Method i : connMethods_) {
                if (i.getName().equals(func)) {
                    m = i;
                    break;
                }
            }
        }
        if (m == null) {
            return ResultInfo.exceptionByMsgs("内部错误: 调用Controller时类名或方法名错误");
        }

        String id = UidUtil.getUID().toString();
        resultMap_.put(id, WaitingForResponse);

        Thread thread=new Thread(new CallRun(ins, m, id, args));
        thread.start();
        return ResultInfo.getOkByJson(id);
    }

    @SuppressWarnings("unused")
    public String changeWebView(boolean toData) {
        try {
            String pageUrl;
            if (toData)
                pageUrl = PAGE_DATA_SINGLES;
            else
                pageUrl = PAGE_CONNECT_DEFAULT;
            Desktop.setWebViewPage(pageUrl);
            return ResultInfo.getOkByJson("");
        } catch (Exception e) {
            return ResultInfo.exception(e);
        }
    }


    @SuppressWarnings("unused")
    public String getResult(String id) {
        String re = resultMap_.get(id);
        if (re == null) {
            return ResultInfo.exceptionByMsgs("内部错误: 尝试获取已经返回的结果");
        }

        if (re == WaitingForResponse) {
            return null;
        }

        resultMap_.remove(id);
        return re;
    }

    static class CallRun implements Runnable {
        Object ins;
        Method m;
        String id;
        Object[] args;
        CallRun (Object ins, Method m, String id, Object[] args) {
            this.ins = ins;
            this.m = m;
            this.id = id;
            this.args = args;
        }

        @Override
        public void run() {
            try
            {
                String re;
                // 变长参数需要再次包装, 否则会抛异常
                if (m.getName().equals("sendCommandLine")) {
                    re = (String) m.invoke(ins, new DataSinglesController.VarLenStrArgs(args));
                } else {
                    re = (String) m.invoke(ins, args);
                }
                resultMap_.put(id, re);
            } catch (Exception e) {
                e.printStackTrace();
                resultMap_.put(id, ResultInfo.exception(e));
            }
        }
    }
}
