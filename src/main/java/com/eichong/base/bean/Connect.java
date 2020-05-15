package com.eichong.base.bean;

import lombok.Data;
@Data
public class Connect {

    //连接名
    private String name;

    //主机
    private String hosts;

    //redis密码
    private String pass;
}
