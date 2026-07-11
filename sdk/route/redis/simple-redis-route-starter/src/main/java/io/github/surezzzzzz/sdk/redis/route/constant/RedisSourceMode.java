package io.github.surezzzzzz.sdk.redis.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Redis 数据源模式
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum RedisSourceMode {

    /**
     * 单节点 Redis
     */
    STANDALONE("standalone", "单节点 Redis"),

    /**
     * Redis Cluster
     */
    CLUSTER("cluster", "Redis Cluster");

    private final String code;
    private final String description;

    public static RedisSourceMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        String lowerCode = code.toLowerCase().trim();
        for (RedisSourceMode mode : values()) {
            if (mode.code.equals(lowerCode)) {
                return mode;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    public static String[] getAllCodes() {
        RedisSourceMode[] modes = values();
        String[] codes = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            codes[i] = modes[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
