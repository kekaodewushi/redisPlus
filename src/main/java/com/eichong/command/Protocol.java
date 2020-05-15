package com.eichong.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class Protocol {

    static final int DEFAULT_TIMEOUT = 3000;

    static final String CHARSET = "UTF-8";
    private static final byte DOLLAR_BYTE = '$';
    private static final byte ASTERISK_BYTE = '*';
    private static final byte PLUS_BYTE = '+';
    private static final byte MINUS_BYTE = '-';
    private static final byte COLON_BYTE = ':';

    private Protocol() {
        // this prevent the class from instantiation
    }

    static void sendCommand(final RedisOutputStream os, final byte[] command,
                                    final byte[]... args) throws JedisException {
        try {
            os.write(ASTERISK_BYTE);
            os.writeIntCrLf(args.length + 1);
            os.write(DOLLAR_BYTE);
            os.writeIntCrLf(command.length);
            os.write(command);
            os.writeCrLf();

            for (final byte[] arg : args) {
                os.write(DOLLAR_BYTE);
                os.writeIntCrLf(arg.length);
                os.write(arg);
                os.writeCrLf();
            }
        } catch (IOException e) {
            throw new JedisException(e);
        }
    }

    static String readErrorLineIfPossible(RedisInputStream is) throws JedisException {
        final byte b = is.readByte();
        // if buffer contains other type of response, just ignore.
        if (b != MINUS_BYTE) {
            return null;
        }
        return is.readLine();
    }

    static RedisReply readReply(final RedisInputStream is)  {
        try {
            final byte b = is.readByte();
            if (b == PLUS_BYTE) {
                return new RedisReply(processStatusCodeReply(is), true);
            } else if (b == DOLLAR_BYTE) {
                return new RedisReply(processBulkReply(is), false);
            } else if (b == ASTERISK_BYTE) {
                return new RedisReply(processMultiBulkReply(is));
            } else if (b == COLON_BYTE) {
                return new RedisReply(processInteger(is));
            } else if (b == MINUS_BYTE) {
                return new RedisReply(is.readLine());
            } else {
                return new RedisReply("Unknown reply: " + (char) b);
            }
        } catch (Exception e) {
            return new RedisReply(e);
        }
    }

    private static byte[] processStatusCodeReply(final RedisInputStream is) throws JedisException {
        return is.readLineBytes();
    }

    private static byte[] processBulkReply(final RedisInputStream is) throws JedisException {
        final int len = is.readIntCrLf();
        if (len == -1) {
            return null;
        }

        final byte[] read = new byte[len];
        int offset = 0;
        while (offset < len) {
            final int size = is.read(read, offset, (len - offset));
            if (size == -1) throw new JedisException(
                    "It seems like server has closed the connection.");
            offset += size;
        }

        // read 2 more bytes for the command delimiter
        is.readByte();
        is.readByte();

        return read;
    }

    private static Long processInteger(final RedisInputStream is) throws JedisException {
        return is.readLongCrLf();
    }

    private static List<RedisReply> processMultiBulkReply(final RedisInputStream is) throws JedisException {
        final int num = is.readIntCrLf();
        if (num == -1) {
            return null;
        }
        final List<RedisReply> ret = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            ret.add(readReply(is));
        }
        return ret;
    }
}
