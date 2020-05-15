package com.redisplus.tool;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class UidUtil {
    private static AtomicLong id_ = new AtomicLong();
    public static Long getUID() {
        return id_.incrementAndGet();
    }
}
