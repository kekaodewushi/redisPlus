package com.redisplus.command;

import org.apache.logging.log4j.util.Strings;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Connection implements Closeable {

    private String host;// = Protocol.DEFAULT_HOST;
    private int port;// = Protocol.DEFAULT_PORT;
    private Socket socket;
    private RedisOutputStream outputStream;
    private RedisInputStream inputStream;
//    private boolean broken = false;

    Connection(final String host, final int port) {
        this.host = host;
        this.port = port;
    }

    public static void checkAdressValid(String host, int port) throws JedisException {
        if (Strings.isEmpty(host))
            throw new JedisException("Server地址为空");
        boolean hasLetters = false;
        for (int i=0; i<host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.')
                continue;
            if (c >= '0' && c <= '9')
                continue;
            if (c == '-' || c == '_')
                continue;
            hasLetters = true;
            if (c >= 'A' && c <= 'Z')
                continue;
            if (c >= 'a' && c <= 'z')
                continue;
            throw new JedisException("Server地址(" +host+")格式错误");
        }
        if (port <= 0)
            throw new JedisException("Server端口(" +port+")错误");

        if(!hasLetters) {
            String[] parts = host.split("\\.");
            boolean ok = true;
            if (parts.length != 4) {
                ok = false;
            } else {
                for (int i=0; i<4; i++) {
                    int num = Integer.parseInt(parts[i]);
                    if (num < 0 || num > 255) {
                        ok = false;
                        break;
                    }
                }
            }
            if (!ok)
                throw new JedisException("Server地址(" +host+")格式错误");
        }
    }

    RedisReply sendCommand(final String cmd, final byte[]... args) {
        try {
            connect();
        } catch (Exception e) {
            return new RedisReply(e.getMessage());
        }

        try {
            Protocol.sendCommand(outputStream, SafeEncoder.encode(cmd), args);
        } catch (JedisException ex) {
            /*
             * When client send request which formed by invalid protocol, Redis send back error message
             * before close connection. We try to read it to provide reason of failure.
             */
            try {
                String errorMessage = Protocol.readErrorLineIfPossible(inputStream);
                if (errorMessage != null && errorMessage.length() > 0) {
                    ex = new JedisException(errorMessage, ex.getCause());
                }
            } catch (Exception e) {
                /*
                 * Catch any IOException or JedisException occurred from InputStream#read and just
                 * ignore. This approach is safe because reading error message is optional and connection
                 * will eventually be closed.
                 */
            }
            // Any other exceptions related to connection?
//            broken = true;
            return new RedisReply(ex);
        }

        return readReply();
    }

    private void connect() throws JedisException {
        if (!isConnected()) {
            checkAdressValid(host, port);
            try {
                socket = new Socket();
                // ->@wjw_add
                socket.setReuseAddress(true);
                socket.setKeepAlive(true); // Will monitor the TCP connection is
                // valid
                socket.setTcpNoDelay(true); // Socket buffer Whetherclosed, to
                // ensure timely delivery of data
                socket.setSoLinger(true, 0); // Control calls close () method,
                // the underlying socket is closed
                // immediately
                // <-@wjw_add

                socket.connect(new InetSocketAddress(host, port), Protocol.DEFAULT_TIMEOUT);
                socket.setSoTimeout(Protocol.DEFAULT_TIMEOUT);

                outputStream = new RedisOutputStream(socket.getOutputStream());
                inputStream = new RedisInputStream(socket.getInputStream());
            } catch (IOException ex) {
//                broken = true;
                throw new JedisException("连接Server地址(" +host + ":" + port + ")失败");
            }
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    private void disconnect() {
        if (isConnected()) {
            try {
                outputStream.flush();
                socket.close();
            } catch (IOException ex) {
//                broken = true;
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // ignored
                    }
                }
            }
        }
    }

    private boolean isConnected() {
        return socket != null && socket.isBound() && !socket.isClosed() && socket.isConnected()
                && !socket.isInputShutdown() && !socket.isOutputShutdown();
    }

    private void flush() throws JedisException {
        try {
            outputStream.flush();
        } catch (IOException ex) {
//            broken = true;
            throw new JedisException(ex);
        }
    }

    private RedisReply readReply() {
        try {
            flush();
        } catch (JedisException e) {
//            broken = true;
            return new RedisReply(e);
        }

        return Protocol.readReply(inputStream);
    }
}
