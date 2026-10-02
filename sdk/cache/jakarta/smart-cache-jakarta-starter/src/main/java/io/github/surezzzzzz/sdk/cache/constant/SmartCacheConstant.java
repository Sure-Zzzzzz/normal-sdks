package io.github.surezzzzzz.sdk.cache.constant;

/**
 * Smart Cache 常量
 *
 * @author surezzzzzz
 */
public final class SmartCacheConstant {

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.cache";

    /**
     * 属性：enabled
     */
    public static final String PROPERTY_ENABLED = "enabled";

    /**
     * 属性值：true
     */
    public static final String PROPERTY_VALUE_TRUE = "true";

    /**
     * RedisRouteTemplate 类名
     */
    public static final String REDIS_ROUTE_TEMPLATE_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate";

    /**
     * Redis Route 自动配置类名
     */
    public static final String REDIS_ROUTE_CONFIGURATION_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration";

    /**
     * Redis Lock 自动配置类名
     */
    public static final String SIMPLE_REDIS_LOCK_CONFIGURATION_CLASS_NAME =
            "io.github.surezzzzzz.sdk.lock.redis.configuration.SimpleRedisLockConfiguration";

    // ==================== Bean 名称常量 ====================

    /**
     * Smart Cache 专用 ObjectMapper Bean 名称
     */
    public static final String SMART_CACHE_OBJECT_MAPPER_BEAN_NAME = "smartCacheObjectMapper";

    /**
     * 缓存序列化器 Bean 名称
     */
    public static final String SMART_CACHE_SERIALIZER_BEAN_NAME = "smartCacheSerializer";

    /**
     * 类型校验器 Bean 名称
     */
    public static final String SMART_CACHE_TYPE_VALIDATOR_BEAN_NAME = "smartCacheTypeValidator";

    /**
     * 预刷新线程池 Bean 名称
     */
    public static final String SMART_CACHE_PRELOAD_EXECUTOR_BEAN_NAME = "smartCachePreloadExecutor";

    /**
     * 启动预热线程池 Bean 名称
     */
    public static final String SMART_CACHE_WARMUP_EXECUTOR_BEAN_NAME = "smartCacheWarmUpExecutor";

    // ==================== 缓存默认值 ====================

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
     * 默认 L2 Key 格式
     */
    public static final String DEFAULT_L2_KEY_FORMAT = "{keyPrefix}:{cacheName}:{me}::{key}";

    /**
     * 默认 Pub/Sub channel 前缀
     */
    public static final String DEFAULT_PUBSUB_CHANNEL_PREFIX = "cache:pubsub";

    /**
     * 默认 route 探测 Key
     */
    public static final String PUBSUB_ROUTE_PROBE_KEY = "route-probe";

    /**
     * route 扫描默认关闭
     */
    public static final boolean DEFAULT_ROUTE_SCAN_ENABLED = false;

    /**
     * 默认 SCAN count
     */
    public static final int DEFAULT_SCAN_COUNT = 100;

    /**
     * 默认预刷新线程数
     */
    public static final int DEFAULT_PRELOAD_EXECUTOR_THREADS = 4;

    /**
     * 默认预刷新队列容量
     */
    public static final int DEFAULT_PRELOAD_EXECUTOR_QUEUE_CAPACITY = 1024;

    /**
     * 默认预热线程数
     */
    public static final int DEFAULT_WARMUP_EXECUTOR_THREADS = 4;

    /**
     * 默认预热队列容量
     */
    public static final int DEFAULT_WARMUP_EXECUTOR_QUEUE_CAPACITY = 1024;

    /**
     * L1 刷新线程数
     */
    public static final int L1_REFRESH_EXECUTOR_THREADS = 2;

    /**
     * 缓存失效消息线程池核心线程数
     */
    public static final int INVALIDATION_EXECUTOR_CORE_THREADS = 2;

    /**
     * 缓存失效消息线程池最大线程数
     */
    public static final int INVALIDATION_EXECUTOR_MAX_THREADS = 4;

    /**
     * 缓存失效消息线程池队列容量
     */
    public static final int INVALIDATION_EXECUTOR_QUEUE_CAPACITY = 1000;

    /**
     * 缓存失效消息线程池空闲线程保留时间（秒）
     */
    public static final long INVALIDATION_EXECUTOR_KEEP_ALIVE_SECONDS = 60L;

    /**
     * L1 刷新线程名前缀
     */
    public static final String L1_REFRESH_THREAD_NAME_PREFIX = "l1-cache-refresh-";

    /**
     * L2 预刷新线程名前缀
     */
    public static final String PRELOAD_THREAD_NAME_PREFIX = "smart-cache-preload-";

    /**
     * 缓存预热线程名前缀
     */
    public static final String WARMUP_THREAD_NAME_PREFIX = "smart-cache-warmup-";

    /**
     * 缓存失效消息线程名前缀
     */
    public static final String INVALIDATION_THREAD_NAME_PREFIX = "cache-invalidation-";

    /**
     * 线程池优雅关闭等待时间（秒）
     */
    public static final long EXECUTOR_SHUTDOWN_AWAIT_SECONDS = 5L;

    /**
     * L1 刷新线程池首次关闭等待时间（秒）
     */
    public static final long L1_REFRESH_SHUTDOWN_AWAIT_SECONDS = 10L;

    /**
     * 缓存依赖链告警深度
     */
    public static final int CACHE_DEPENDENCY_WARN_DEPTH = 10;

    /**
     * 重试最小延迟（毫秒）
     */
    public static final long MIN_RETRY_DELAY_MILLIS = 1000L;

    /**
     * 默认可信包：java.lang
     */
    public static final String TRUSTED_PACKAGE_JAVA_LANG = "java.lang";

    /**
     * 默认可信包：java.time
     */
    public static final String TRUSTED_PACKAGE_JAVA_TIME = "java.time";

    /**
     * 默认可信包：java.util
     */
    public static final String TRUSTED_PACKAGE_JAVA_UTIL = "java.util";

    /**
     * Pub/Sub 模式：路由模式
     */
    public static final String PUBSUB_MODE_ROUTED = "routed";

    /**
     * Pub/Sub 模式：关闭
     */
    public static final String PUBSUB_MODE_DISABLED = "disabled";

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
     * 一致性模式：强一致性
     */
    public static final String CONSISTENCY_MODE_STRONG = "strong";

    /**
     * 一致性模式：最终一致性
     */
    public static final String CONSISTENCY_MODE_EVENTUAL = "eventual";

    /**
     * 空值占位符
     */
    public static final Object NULL_PLACEHOLDER = new Object() {
        @Override
        public String toString() {
            return "NULL_PLACEHOLDER";
        }
    };

    /**
     * 空值缓存 TTL（秒）
     */
    public static final int NULL_CACHE_TTL_SECONDS = 60;

    /**
     * 分布式锁 Key 后缀
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
     * Pub/Sub 频道后缀
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
     * 预热失败策略：继续启动
     */
    public static final String WARMUP_FAILURE_POLICY_CONTINUE = "continue";

    /**
     * 预热失败策略：快速失败
     */
    public static final String WARMUP_FAILURE_POLICY_FAIL_FAST = "fail-fast";

    /**
     * 默认预热失败策略
     */
    public static final String DEFAULT_WARMUP_FAILURE_POLICY = WARMUP_FAILURE_POLICY_CONTINUE;

    /**
     * 预热完成标记默认 TTL（秒）
     */
    public static final int DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS = 600;

    /**
     * 预热完成标记 TTL 最小值（秒）
     */
    public static final int MIN_WARMUP_COMPLETION_MARK_TTL_SECONDS = 60;

    /**
     * 预热完成标记 TTL 最大值（秒）
     */
    public static final int MAX_WARMUP_COMPLETION_MARK_TTL_SECONDS = 3600;

    // ==================== 分布式锁默认值 ====================

    /**
     * 分布式锁默认超时（秒）
     */
    public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 30;

    /**
     * 分布式锁超时最小值（秒）
     */
    public static final int MIN_LOCK_TIMEOUT_SECONDS = 5;

    /**
     * 分布式锁超时最大值（秒）
     */
    public static final int MAX_LOCK_TIMEOUT_SECONDS = 300;

    private SmartCacheConstant() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
