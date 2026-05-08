package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * @author: Sure.
 * @description 智能Redis限流器Key常量
 * @Date: 2024/12/XX XX:XX
 */
public interface SmartRedisLimiterRedisKeyConstant {

    /**
     * Key前缀
     */
    String KEY_PREFIX = "smart-limiter:";

    /**
     * Key分隔符
     */
    String KEY_SEPARATOR = ":";

    /**
     * Hash Tag左括号（集群模式）
     */
    String HASH_TAG_LEFT = "{";

    /**
     * Hash Tag右括号（集群模式）
     */
    String HASH_TAG_RIGHT = "}";

    /**
     * 秒单位后缀
     */
    String SUFFIX_SECONDS = "s";

    /**
     * 分单位后缀
     */
    String SUFFIX_MINUTES = "m";

    /**
     * 时单位后缀
     */
    String SUFFIX_HOURS = "h";

    /**
     * 天单位后缀
     */
    String SUFFIX_DAYS = "d";
}
