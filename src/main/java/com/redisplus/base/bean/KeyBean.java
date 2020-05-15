package com.redisplus.base.bean;

import lombok.Data;

@Data
public class KeyBean {

    private long ttl;

    private long size;

    private String key;

    private String type;

    private String text;

    private String json;

    private Object data;
}
