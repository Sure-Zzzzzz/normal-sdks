package io.github.surezzzzzz.sdk.cache.constant;

/**
 * Smart Cache Constants
 * <p>
 * 缓存相关常量定义
 * </p>
 *
 * @author Sure
 */
public final class SmartCacheConstant {

    /**
     * Redis Key 前缀
     */
    public static final String REDIS_KEY_PREFIX = "sure-cache";

    /**
     * 默认缓存名称
     */
    public static final String DEFAULT_CACHE_NAME = "default";

    /**
     * 默认实例标识
     */
    public static final String DEFAULT_INSTANCE_ID = "default";

    /**
     * Redis Hash Tag 前缀
     */
    public static final String HASH_TAG_PREFIX = "{";

    /**
     * Redis Hash Tag 后缀
     */
    public static final String HASH_TAG_SUFFIX = "}";

    /**
     * 缓存键分隔符
     */
    public static final String KEY_SEPARATOR = ":";

    /**
     * 缓存操作类型：删除单个
     */
    public static final String OPERATION_EVICT = "evict";

    /**
     * 缓存操作类型：清空所有
     */
    public static final String OPERATION_CLEAR = "clear";

    /**
     * 一致性模式：强一致性（Pub/Sub 实时同步）
     */
    public static final String CONSISTENCY_MODE_STRONG = "strong";

    /**
     * 一致性模式：最终一致性（依赖 TTL）
     */
    public static final String CONSISTENCY_MODE_EVENTUAL = "eventual";

    /**
     * 空值占位符（防止缓存穿透）
     * <p>
     * 使用单例对象作为标记，避免字符串比较开销
     * 该对象仅用于内部标识，不会被序列化到 Redis
     * </p>
     */
    public static final Object NULL_PLACEHOLDER = new Object() {
        @Override
        public String toString() {
            return "NULL_PLACEHOLDER";
        }
    };

    /**
     * 空值缓存TTL（秒）
     */
    public static final int NULL_CACHE_TTL_SECONDS = 60;

    /**
     * 分布式锁Key后缀
     */
    public static final String LOCK_KEY_SUFFIX = "-lock";

    /**
     * 预热锁 key 标识
     */
    public static final String WARMUP_LOCK_KEY = "warmup";

    /**
     * 预热完成标记 key 后缀
     */
    public static final String WARMUP_COMPLETE_KEY_SUFFIX = "warmup-complete";

    /**
     * 预热 keys 列表 key 后缀
     */
    public static final String WARMUP_KEYS_KEY_SUFFIX = "warmup-keys";

    /**
     * 预热完成标记值
     */
    public static final String WARMUP_COMPLETE_MARK_VALUE = "1";

    /**
     * 预热锁等待超时时间（秒）
     */
    public static final int WARMUP_LOCK_TIMEOUT_SECONDS = 30;

    /**
     * 预热完成标记等待超时时间（秒）
     */
    public static final int WARMUP_WAIT_TIMEOUT_SECONDS = 60;

    /**
     * 预热完成标记轮询间隔（毫秒）
     */
    public static final int WARMUP_POLL_INTERVAL_MILLIS = 500;

    /**
     * SpEL 表达式中的返回值变量名
     */
    public static final String SPEL_RESULT_VARIABLE = "result";

    /**
     * Pub/Sub 频道后缀（用于缓存失效通知）
     */
    public static final String PUBSUB_CHANNEL_SUFFIX = "-cache-invalidation";

    /**
     * L2 预刷新分布式锁 key 后缀
     */
    public static final String PRELOAD_LOCK_KEY_SUFFIX = ":preload-lock:";

    /**
     * L2 预刷新重试次数
     */
    public static final int PRELOAD_MAX_RETRIES = 6;

    /**
     * L2 预刷新重试退避倍数
     */
    public static final double PRELOAD_RETRY_BACKOFF_RATIO = 1.5;

    /**
     * L2 异步续期默认提前量（秒）
     */
    public static final int DEFAULT_L2_PRELOAD_BEFORE_EXPIRE_SECONDS = 300;

    // ==================== L1 缓存默认值 ====================

    /**
     * L1 默认最大容量
     */
    public static final int DEFAULT_L1_MAX_SIZE = 10000;

    /**
     * L1 默认过期时间（秒）
     */
    public static final int DEFAULT_L1_EXPIRE_SECONDS = 300;

    /**
     * L1 默认刷新时间（秒）
     */
    public static final int DEFAULT_L1_REFRESH_SECONDS = 270;

    /**
     * L1 刷新时间最小值（秒）
     */
    public static final int MIN_L1_REFRESH_SECONDS = 10;

    /**
     * L1 刷新时间调整时距过期时间的保留缓冲（秒）
     * 当 refreshSeconds >= expireSeconds 时，自动调整为 expireSeconds - 此值
     */
    public static final int L1_REFRESH_EXPIRE_BUFFER_SECONDS = 30;

    /**
     * L1 最大容量上限
     */
    public static final int MAX_L1_MAX_SIZE = 1000000;

    // ==================== L2 缓存默认值 ====================

    /**
     * L2 默认过期时间（秒）
     */
    public static final int DEFAULT_L2_EXPIRE_SECONDS = 3600;

    /**
     * L2 默认 TTL 随机偏移比例
     */
    public static final double DEFAULT_L2_TTL_RANDOM_OFFSET_RATIO = 0.1;

    // ==================== 预热默认值 ====================

    /**
     * 预热完成标记默认 TTL（秒）
     */
    public static final int DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS = 600;

    /**
     * 预热完成标记 TTL 范围
     */
    public static final int MIN_WARMUP_COMPLETION_MARK_TTL_SECONDS = 60;
    public static final int MAX_WARMUP_COMPLETION_MARK_TTL_SECONDS = 3600;

    // ==================== 分布式锁默认值 ====================

    /**
     * 分布式锁默认超时（秒）
     */
    public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 30;

    /**
     * 分布式锁超时范围
     */
    public static final int MIN_LOCK_TIMEOUT_SECONDS = 5;
    public static final int MAX_LOCK_TIMEOUT_SECONDS = 300;

    private SmartCacheConstant() {
        throw new UnsupportedOperationException("Constant class cannot be instantiated");
    }
}
