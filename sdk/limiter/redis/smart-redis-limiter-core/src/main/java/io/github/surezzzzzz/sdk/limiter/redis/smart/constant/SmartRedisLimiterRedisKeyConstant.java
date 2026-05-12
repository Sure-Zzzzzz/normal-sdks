package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter Redis Key 常量
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterRedisKeyConstant {

    private SmartRedisLimiterRedisKeyConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Key 前缀 ====================

    /**
     * Key前缀
     */
    public static final String KEY_PREFIX = "smart-limiter:";

    /**
     * Key分隔符
     */
    public static final String KEY_SEPARATOR = ":";

    // ==================== Hash Tag（集群模式）====================

    /**
     * Hash Tag左括号
     */
    public static final String HASH_TAG_LEFT = "{";

    /**
     * Hash Tag右括号
     */
    public static final String HASH_TAG_RIGHT = "}";

    // ==================== 时间单位后缀 ====================

    /**
     * 秒单位后缀
     */
    public static final String SUFFIX_SECONDS = "s";

    /**
     * 分单位后缀
     */
    public static final String SUFFIX_MINUTES = "m";

    /**
     * 时单位后缀
     */
    public static final String SUFFIX_HOURS = "h";

    /**
     * 天单位后缀
     */
    public static final String SUFFIX_DAYS = "d";

    // ==================== 限流算法标识 ====================

    /**
     * 滑动窗口算法标识
     */
    public static final String SUFFIX_SLIDING_WINDOW = "sw";

    // ==================== 时间换算 ====================

    /**
     * 每秒纳秒数（滑动窗口算法使用纳秒精度）
     */
    public static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;
}
