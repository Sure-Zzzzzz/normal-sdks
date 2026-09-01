package io.github.surezzzzzz.sdk.redis.route.constant;

import lombok.Getter;

/**
 * Redis Cluster 读偏好
 *
 * @author surezzzzzz
 */
@Getter
public enum RedisReadFrom {

    /**
     * 仅从主节点读取
     */
    MASTER("master", "仅从主节点读取"),

    /**
     * 优先从主节点读取
     */
    MASTER_PREFERRED("master-preferred", "优先从主节点读取"),

    /**
     * 仅从副本节点读取
     */
    REPLICA("replica", "仅从副本节点读取"),

    /**
     * 优先从副本节点读取
     */
    REPLICA_PREFERRED("replica-preferred", "优先从副本节点读取"),

    /**
     * 从最近节点读取
     */
    NEAREST("nearest", "从最近节点读取"),

    /**
     * 从任意节点读取
     */
    ANY("any", "从任意节点读取");

    private final String code;
    private final String description;

    RedisReadFrom(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按代码获取读偏好。
     *
     * @param code 读偏好代码
     * @return 读偏好，不存在时返回 null
     */
    public static RedisReadFrom fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RedisReadFrom readFrom : values()) {
            if (readFrom.code.equalsIgnoreCase(code)) {
                return readFrom;
            }
        }
        return null;
    }

    /**
     * 判断读偏好代码是否有效。
     *
     * @param code 读偏好代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部读偏好代码。
     *
     * @return 读偏好代码数组
     */
    public static String[] getAllCodes() {
        RedisReadFrom[] readFroms = values();
        String[] codes = new String[readFroms.length];
        for (int i = 0; i < readFroms.length; i++) {
            codes[i] = readFroms[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
