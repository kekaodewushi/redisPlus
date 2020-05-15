package com.eichong.base.controller;

import com.alibaba.fastjson.JSON;
import com.eichong.base.bean.Connect;
import com.eichong.base.bean.OneFieldBean;
import com.eichong.base.bean.ZTreeBean;
import com.eichong.base.service.ConnSrv;
import com.eichong.command.JedisException;
import com.eichong.base.service.RedisSrv;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.eichong.base.bean.ResultInfo.*;

/**
 * 单机模式下数据处理器
 */
@Component
public class DataSinglesController {

    /**
     * 初始化DB树
     */
    @SuppressWarnings("unused")
    public String treeInit(String pattern) {
        try {
            RedisSrv.PathAndKeys pathAndKeys = RedisSrv.searchPath(pattern);
            Connect conn = ConnSrv.getCurrentConn();
            String title;
            if (conn != null)
                title = conn.getName();
            else
                title = "?";

            if (Strings.isEmpty(pathAndKeys.path) || pathAndKeys.path.equals("*")) {
                if (conn != null)
                    title += "[" + conn.getHosts() + "]";
                // 避免title中的内容与path与title的默认分隔符冲突
                title = title.replace('(', '[');
                title = title.replace(')', ']');
            } else {
                // 避免title中的内容与path与title的默认分隔符冲突
                title = title.replace('(', '[');
                title = title.replace(')', ']');
                title = pathAndKeys.path + "(" + title + ")";
            }

            ZTreeBean ztreeBean = new ZTreeBean("root", title);
            ztreeBean.setParent(true);
            ztreeBean.setCount(pathAndKeys.keys);
            ztreeBean.setPattern(Strings.isEmpty(pattern)?"*":pattern);
            return getOkByJson(ztreeBean);
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

    @SuppressWarnings("unused")
    public String treeData(String id, String name, String pattern) {
        try {
            if (Strings.isEmpty(pattern)) {
                pattern = "*";
            }
            String path = pattern;
            if (!pattern.equals("*")) {
                int pos = name.lastIndexOf('(');
                if (pos > 0)
                    path = name.substring(0, pos);
                else if (id.equals("root")) // root
                    path = "*"; // no filter
            }
//            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//            System.out.println(df.format(new Date()) + " java treeData");
            List<ZTreeBean> beanList = RedisSrv.treeData(id, path, pattern);
//            System.out.println(df.format(new Date()) + " beanList:" + beanList.size());
            String re = getOkByJson(beanList);
//            System.out.println(df.format(new Date()) + " getOkByJson:" + re.length());
            return re;
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

    @SuppressWarnings("unused")
    public String delFilteredTree(String name, String pattern) {
        try {
            if (Strings.isEmpty(pattern)) {
                pattern = "*";
            }
            String path = pattern;
            if (!pattern.equals("*")) {
                int pos = name.lastIndexOf('(');
                if (pos > 0)
                    path = name.substring(0, pos);
            }
            RedisSrv.delFilteredTree(path, pattern);
            return getOkByJson("批量删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

    /**
     * 查询KEY详细信息
     */
    @SuppressWarnings("unused")
    public String keysData(String keys) {
        try {
            return getOkByJson(RedisSrv.getKeyInfo(keys));
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

    static class VarLenStrArgs {
        Object[] objs;
        VarLenStrArgs(Object[] objs) {
            this.objs = objs;
        }
        String[] get() throws JedisException {
            String[] re = new String[objs.length];
            int i = 0;
            for (Object o : objs) {
                if (o instanceof String) {
                    re[i++] = (String)o;
                } else {
                    throw new JedisException("第" + i + "参数 "+o+" 类型不对, 不是String");
                }
            }
            return re;
        }
    }
    @SuppressWarnings("unused")
    public String sendCommandLine(VarLenStrArgs args) {
        try {
            RedisSrv.sendCommandLine(args.get());
            return getOkByJson("KEY上的操作成功");
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

    /**
     * 更新ITEM数据, 添加成功新的之后再删之前的
     */
    @SuppressWarnings("unused")
    public String updateVal(int type, String key, String oldVal, String idxOrScoreOrField, String newVal) {
        String[] valArray;
        String i, key_i, val_i;
        try {
            //1:set,2:zset,3:list,4:hash
            switch (type) {
                case 1:
                    if (!newVal.equals(oldVal)) {
                        RedisSrv.sendCommandLine("SADD", key, newVal);
                        RedisSrv.sendCommandLine("SREM", key, oldVal);
                    }
                    break;
                case 2:
                    RedisSrv.sendCommandLine("ZADD", key, idxOrScoreOrField, newVal);
                    if (!newVal.equals(oldVal)) {
                        RedisSrv.sendCommandLine("ZREM", key, oldVal);
                    }
                    break;
                case 3:
                    RedisSrv.sendCommandLine("LSET", key, idxOrScoreOrField, newVal);
                    break;
                case 4:
                    RedisSrv.sendCommandLine("HSET", key, idxOrScoreOrField, newVal);
                    if (!idxOrScoreOrField.equals(oldVal)) {
                        RedisSrv.sendCommandLine("HDEL", key, oldVal);
                    }
                    break;
            }
            return getOkByJson("修改数据成功");
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

    private String[] wrapStrArray(String cmd, String key, List<OneFieldBean> l) {
        String[] re = new String[2 + l.size()];
        int i = 0;
        re[i++] = cmd;
        re[i++] = key;
        for (OneFieldBean item : l) {
            re[i++] = item.getField();
        }
        return re;
    }
    @SuppressWarnings("unused")
    public String delVal(int type, String key, String json) {
        List<OneFieldBean> l = JSON.parseArray(json, OneFieldBean.class);
        try {
            //1:set,2:zset,3:list,4:hash
            switch (type) {
                case 1:
                    RedisSrv.sendCommandLine(wrapStrArray("SREM", key, l));
                    break;
                case 2:
                    RedisSrv.sendCommandLine(wrapStrArray("ZREM", key, l));
                    break;
                case 3:
                    RedisSrv.sendCommandLine(wrapStrArray("LDEL", key, l));
                    break;
                case 4:
                    RedisSrv.sendCommandLine(wrapStrArray("HDEL", key, l));
                    break;
            }
            return getOkByJson("删除数据成功");
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

    /**
     * 新增KEY数据, 添加成功新的之后再设置ttl
     */
    @SuppressWarnings("unused")
    public String insertKey(String strType, String key, String val, String seconds, String scoreOrField) {
        if (RedisSrv.exists(key)) {
            return getErrByJson("新KEY已经存在");
        }
        try {
            int type = Integer.parseInt(strType);
            //1:set,2:zset,3:list,4:hash
            switch (type) {
                case 1:
                    RedisSrv.sendCommandLine("SADD", key, val);
                    break;
                case 2:
                    RedisSrv.sendCommandLine("ZADD", key, scoreOrField, val);
                    break;
                case 3:
                    RedisSrv.sendCommandLine("RPUSH", key, val);
                    break;
                case 4:
                    RedisSrv.sendCommandLine("HSET", key, scoreOrField, val);
                    break;
                case 5:
                    RedisSrv.sendCommandLine("SET", key, val);
                    break;
                default:
                    return exceptionByMsgs("不支持的数据类型");
            }
            if (Integer.parseInt(seconds) > 0) {
                RedisSrv.sendCommandLine("EXPIRE", key, seconds);
            }
            return getOkByJson("新增数据成功");
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

    @SuppressWarnings("unused")
    public static String renameKey(String oldKey, String newKey) {
        if (RedisSrv.exists(newKey)) {
            return getErrByJson("新KEY已经存在");
        }
        try {
            RedisSrv.renameKey(oldKey, newKey);
            return getOkByJson("重命名成功");
        } catch (Exception e) {
            e.printStackTrace();
            return exception(e);
        }
    }

}
