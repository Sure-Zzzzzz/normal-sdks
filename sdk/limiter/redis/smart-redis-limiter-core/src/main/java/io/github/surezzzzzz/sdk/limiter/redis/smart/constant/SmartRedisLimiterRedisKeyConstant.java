package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter Redis Key 常量
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterRedisKeyConstant {

    // ==================== Key 前缀 ====================

    /**
     * Key前缀
     */
    String KEY_PREFIX = "smart-limiter:";

    /**
     * Key分隔符
     */
    String KEY_SEPARATOR = ":";

    // ==================== Hash Tag（集群模式）====================

    /**
     * Hash Tag左括号
     */
    String HASH_TAG_LEFT = "{";

    /**
     * Hash Tag右括号
     */
    String HASH_TAG_RIGHT = "}";

    // ==================== 时间单位后缀 ====================

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

    // ==================== 限流算法标识 ====================

    /**
     * 滑动窗口算法标识
     */
    String SUFFIX_SLIDING_WINDOW = "sw";
}
