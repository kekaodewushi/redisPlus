package com.redisplus.command;

import java.io.IOException;

public class JedisException extends IOException {
    private static final long serialVersionUID = -2946266495682282677L;

    public JedisException(String message) {
        super(message);
    }

    JedisException(Throwable e) {
        super(e);
    }

    JedisException(String message, Throwable cause) {
        super(message, cause);
    }
}
