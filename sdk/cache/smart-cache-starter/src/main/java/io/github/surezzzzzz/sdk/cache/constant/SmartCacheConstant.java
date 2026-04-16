package io.github.surezzzzzz.sdk.cache.constant;

/**
 * Smart Cache Constants
 * <p>
 * 缓存相关常量定义
 * </p>
 *
 * @author Sure
 */
public class SmartCacheConstant {

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
     * 预热完成标记 TTL（秒）
     * 必须大于 WARMUP_WAIT_TIMEOUT_SECONDS，确保等待期间标记不会过期
     */
    public static final int WARMUP_COMPLETE_MARK_TTL_SECONDS = 120;

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

    private SmartCacheConstant() {
        throw new UnsupportedOperationException("Constant class cannot be instantiated");
    }
}
