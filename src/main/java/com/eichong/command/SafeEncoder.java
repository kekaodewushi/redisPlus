package com.eichong.command;

import java.io.UnsupportedEncodingException;

/**
 * The only reason to have this is to be able to compatible with java 1.5 :(
 */
public final class SafeEncoder {
    private SafeEncoder(){
        throw new InstantiationError( "Must not instantiate this class" );
    }

//    public static byte[][] encodeMany(final String... strs) throws JedisException {
//        byte[][] many = new byte[strs.length][];
//        for (int i = 0; i < strs.length; i++) {
//            many[i] = encode(strs[i]);
//        }
//        return many;
//    }

    public static byte[] encode(final String str) throws JedisException {
        try {
            if (str == null) {
                throw new JedisException("value sent to redis cannot be null");
            }
            return str.getBytes(Protocol.CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new JedisException(e);
        }
    }

    public static String encode(final byte[] data) throws JedisException {
        try {
            return new String(data, Protocol.CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new JedisException(e);
        }
    }

    public static byte[] encode(final int value) {
        try {
            return encode(String.valueOf(value));
        } catch (JedisException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }
    
    public static byte[] encode(final long value) {
        try {
            return encode(String.valueOf(value));
        } catch (JedisException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static byte[] encode(final double value) {
        if (Double.isInfinite(value)) {
            return value == Double.POSITIVE_INFINITY ? "+inf".getBytes() : "-inf".getBytes();
        }
        try {
            return encode(String.valueOf(value));
        } catch (JedisException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

//    private static final byte[] BYTES_TRUE = encode(1);
//    private static final byte[] BYTES_FALSE = encode(0);
//    public static final byte[] toByteArray(final boolean value) {
//        return value ? BYTES_TRUE : BYTES_FALSE;
//    }
}
