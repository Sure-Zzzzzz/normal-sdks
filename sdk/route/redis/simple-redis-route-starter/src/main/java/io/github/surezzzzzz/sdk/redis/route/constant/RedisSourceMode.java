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

    /**
     * 按配置编码解析数据源模式。
     *
     * @param code 模式编码，大小写不敏感
     * @return 对应的数据源模式；编码为空或不受支持时返回 null
     */
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

    /**
     * 判断配置编码是否对应受支持的数据源模式。
     *
     * @param code 模式编码
     * @return 编码受支持时返回 true
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部受支持的数据源模式编码。
     *
     * @return 按枚举声明顺序排列的模式编码
     */
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
