package com.redisplus.command;

import java.util.LinkedList;
import java.util.List;

public class RedisReply {
    public enum Type{ERROR,STATUS,INTEGER,BULKSTR,ARRAY}

    private Type type;
    private Long integer; // The integer when type is INTEGER
    private String status;
    private byte[] bulkstr; // status string or bulk strings
    private List<RedisReply> elements; // for type ARRAY

    RedisReply(String error) {
        type = Type.ERROR;
        status = error;
    }

    RedisReply(Exception e) {
        type = Type.ERROR;
        status = e.getMessage();
    }

    RedisReply(byte[] bytes, boolean status) {
        if (status) {
            type = Type.STATUS;
            try {
                this.status = SafeEncoder.encode(bytes);
            } catch (JedisException e) {
                this.status = e.getMessage();
            }
        } else {
            type = Type.BULKSTR;
            bulkstr = bytes;
        }
    }

    RedisReply(long integer) {
        type = Type.INTEGER;
        this.integer = integer;
    }

    RedisReply(List<RedisReply> l) {
        type = Type.ARRAY;
        elements = l;
    }

    public void addElement(RedisReply e) {
        elements.add(e);
    }

    public void merge(RedisReply other) throws JedisException {
        if (other == null) {
            throw new JedisException("合并多个方向回应的结果时出错: 尝试合并空的结果");
        }
        if (! type.equals(other.type) ) {
            throw new JedisException("合并多个方向回应的结果时出错: 类型不一致");
        }

        if (type.equals(Type.STATUS)) {
            if (status == null)
                status = "";
            status += "\n";
            if (other.status != null)
                status += other.status;
        } else if (type.equals(Type.ARRAY)) {
            if (elements == null)
                elements = new LinkedList<>();
            if (other.elements != null)
                elements.addAll(other.elements);
        } else if (type.equals(Type.BULKSTR)) {
            byte[] old = bulkstr;
            if (old == null)
                old = new byte[0];
            bulkstr = new byte[old.length + 1 + other.bulkstr.length];
            System.arraycopy(old, 0, bulkstr, 0, old.length);
            bulkstr[old.length] = '\n';
            if (other.bulkstr != null)
                System.arraycopy(other.bulkstr, 0, bulkstr, old.length+1, other.bulkstr.length);
        } else if (type.equals(Type.INTEGER)) {
            if (integer == null)
                integer = (long)0;
            if (other.integer != null)
                integer += other.integer;
        } else {
            throw new JedisException("合并多个方向回应的结果时出错: 不支持的类型" + type);
        }
    }

//    void addPeerInfo(String peers) {
//        if (! Type.STATUS.equals(type))
//            return;
//        status = "(" + peers + ")" + status;
//    }

    boolean sameType(RedisReply o) {
        if (o == null || o.type == null)
            return false;
        return o.type.equals(type);
    }

    public boolean replyError() {
        return Type.ERROR.equals(type);
    }

    public String basicTypeToString() throws JedisException {
        if (type == RedisReply.Type.STATUS) {
            return status;
        }
        if (type == RedisReply.Type.INTEGER) {
            return integer.toString();
        }
        if (isBulkStr()) {
            return SafeEncoder.encode(bulkstr);
        }
        throw new JedisException("basicTypeToString: 不是基本类型");
    }

    boolean isBulkStr() {
        return Type.BULKSTR.equals(type);
    }

    public boolean isArrayType() {
        return Type.ARRAY.equals(type);
    }

    public Long getInteger() {
        return integer;
    }

    public String getStatus() {
        if (status.contains("WRONGTYPE"))
            status = "KEY已存在，而且有其它类型的值";
        return status;
    }

    public byte[] getBulkStr() {
        return bulkstr;
    }

    public List<RedisReply> getArray() {
        return elements;
    }
}
