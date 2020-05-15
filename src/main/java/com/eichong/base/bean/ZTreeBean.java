package com.eichong.base.bean;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.LinkedList;

@Data
public class ZTreeBean {

    private String id;

    @JSONField(name = "pId")
    private String pId;

    private String name;

    private String pattern = "";

    private Long count;

    private boolean checked = false;

    @JSONField(name = "isParent")
    private boolean isParent;

    private LinkedList<ZTreeBean> children;

    public ZTreeBean(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public ZTreeBean(String id, String pId, String name, String pattern, boolean isParent) {
        this.id = id;
        this.pId = pId;
        this.name = name;
        this.pattern = pattern;
        this.isParent = isParent;
    }

    public ZTreeBean(String id, String pId, String name, String pattern, boolean isParent, LinkedList<ZTreeBean> children) {
        this.id = id;
        this.pId = pId;
        this.name = name;
        this.pattern = pattern;
        this.isParent = isParent;
        this.children = children;
    }
}
