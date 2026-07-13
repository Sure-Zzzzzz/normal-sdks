package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter 常量定义
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterConstant {

    private SmartRedisLimiterConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.limiter.redis.smart";

    /**
     * 默认：是否启用（false）
     */
    public static final boolean DEFAULT_ENABLE = false;

    /**
     * 默认：服务标识最大长度
     */
    public static final int MAX_ME_LENGTH = 50;

    /**
     * 默认：限流通过时是否发布事件（false）
     */
    public static final boolean DEFAULT_LOG_ON_PASS = false;

    /**
     * 默认：拦截器是否启用（true）
     */
    public static final boolean DEFAULT_INTERCEPTOR_ENABLED = true;

    /**
     * 默认：管理接口异常处理器是否启用（true）
     */
    public static final boolean DEFAULT_EXCEPTION_HANDLER_ENABLED = true;

    /**
     * 默认：Redis 命令超时（毫秒）
     */
    public static final long DEFAULT_COMMAND_TIMEOUT = 3000L;

    /**
     * 默认：是否使用 Redis Cluster Hash Tag
     */
    public static final boolean DEFAULT_USE_HASH_TAG = true;

    /**
     * 默认：限流执行超时保护线程池大小
     */
    public static final int DEFAULT_TIMEOUT_EXECUTOR_THREADS =
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    /**
     * 默认：限流执行超时保护队列容量
     */
    public static final int DEFAULT_TIMEOUT_EXECUTOR_QUEUE_CAPACITY = 1024;

    /**
     * Redis 命令超时下限警告阈值（毫秒）
     */
    public static final long COMMAND_TIMEOUT_MIN_WARNING = 100L;

    /**
     * Redis 命令超时上限警告阈值（毫秒）
     */
    public static final long COMMAND_TIMEOUT_MAX_WARNING = 10000L;

    /**
     * 限流计数上限警告阈值
     */
    public static final int MAX_COUNT_WARNING = 1000000;

    /**
     * 时间窗口上限警告阈值（秒，即24小时）
     */
    public static final long MAX_WINDOW_SECONDS_WARNING = 86400L;

    // ==================== 来源标识 ====================

    /**
     * 来源：拦截器模式
     */
    public static final String SOURCE_INTERCEPTOR = "INTERCEPTOR";

    /**
     * 来源：注解模式
     */
    public static final String SOURCE_ASPECT = "ASPECT";

    /**
     * 事件 keyStrategy 字段中标识自定义 KeyProvider 的前缀
     * <p>实际值为 EVENT_KEY_STRATEGY_CUSTOM_PREFIX + keyProviderName。</p>
     */
    public static final String EVENT_KEY_STRATEGY_CUSTOM_PREFIX = "custom:";

    // ==================== Redis 模式 ====================

    /**
     * 单机模式
     */
    public static final String REDIS_MODE_STANDALONE = "standalone";

    /**
     * 集群模式
     */
    public static final String REDIS_MODE_CLUSTER = "cluster";

    /**
     * 未知模式
     */
    public static final String REDIS_MODE_UNKNOWN = "unknown";

    // ==================== 降级原因 ====================

    /**
     * Redis Route 异常
     */
    public static final String FALLBACK_REASON_ROUTE_ERROR = "route_error";

    /**
     * Redis 执行异常
     */
    public static final String FALLBACK_REASON_REDIS_ERROR = "redis_error";

    /**
     * Lua 脚本返回异常
     */
    public static final String FALLBACK_REASON_SCRIPT_ERROR = "script_error";

    /**
     * KeyProvider 执行异常
     */
    public static final String FALLBACK_REASON_KEY_PROVIDER_ERROR = "key_provider_error";

    /**
     * Redis 执行超时
     */
    public static final String FALLBACK_REASON_TIMEOUT = "timeout";

    /**
     * 执行线程中断
     */
    public static final String FALLBACK_REASON_INTERRUPTED = "interrupted";

    /**
     * 未知异常
     */
    public static final String FALLBACK_REASON_UNKNOWN = "unknown";

    // ==================== Redis Route 类名 ====================

    /**
     * RedisRouteTemplate 全限定类名
     */
    public static final String REDIS_ROUTE_TEMPLATE_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate";

    /**
     * SimpleRedisRouteConfiguration 全限定类名
     */
    public static final String REDIS_ROUTE_CONFIGURATION_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration";

    // ==================== 注解 ====================

    /**
     * 注解全限定名
     */
    public static final String ANNOTATION_CLASS_NAME = "io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter";

    // ==================== 缓存 Key ====================

    /**
     * 规则缓存Key分隔符
     */
    public static final String CACHE_KEY_SEPARATOR = ":";

    // ==================== HTTP ====================

    /**
     * HTTP 429 状态码
     */
    public static final int HTTP_STATUS_TOO_MANY_REQUESTS = 429;

    /**
     * HTTP 429 状态消息
     */
    public static final String HTTP_MESSAGE_TOO_MANY_REQUESTS = "Too Many Requests";

    /**
     * Retry-After 响应头
     */
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    /**
     * X-RateLimit-Limit 响应头（时间窗口内的限流阈值）
     */
    public static final String HEADER_X_RATELIMIT_LIMIT = "X-RateLimit-Limit";

    /**
     * X-RateLimit-Remaining 响应头（当前窗口剩余配额）
     */
    public static final String HEADER_X_RATELIMIT_REMAINING = "X-RateLimit-Remaining";

    /**
     * X-RateLimit-Reset 响应头（窗口重置的 Unix 时间戳，秒）
     */
    public static final String HEADER_X_RATELIMIT_RESET = "X-RateLimit-Reset";

    /**
     * X-Forwarded-For 请求头
     */
    public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * X-Real-IP 请求头
     */
    public static final String HEADER_X_REAL_IP = "X-Real-IP";

    /**
     * IP 未知标识
     */
    public static final String IP_UNKNOWN = "unknown";

    // ==================== 时间单位后缀 ====================

    /**
     * 秒后缀
     */
    public static final String SUFFIX_SECONDS = "s";

    /**
     * 分后缀
     */
    public static final String SUFFIX_MINUTES = "m";

    /**
     * 时后缀
     */
    public static final String SUFFIX_HOURS = "h";

    /**
     * 天后缀
     */
    public static final String SUFFIX_DAYS = "d";

    // ==================== 限流算法 ====================

    /**
     * 算法：固定窗口
     */
    public static final String ALGORITHM_FIXED = "fixed";

    /**
     * 算法：滑动窗口
     */
    public static final String ALGORITHM_SLIDING = "sliding";

    // ==================== Key 模板 ====================

    /**
     * Key 模板：IP 策略
     * 参数: prefix, clientIp
     */
    public static final String TEMPLATE_KEY_IP = "%s:%s";

    /**
     * Key 模板：方法策略
     * 参数: prefix, className, methodName
     */
    public static final String TEMPLATE_KEY_METHOD = "%s:%s.%s";

    /**
     * Key 模板：路径策略（含HTTP方法）
     * 参数: prefix, path, httpMethod
     */
    public static final String TEMPLATE_KEY_PATH_WITH_METHOD = "%s:%s:%s";

    /**
     * Key 模板：路径策略（不含HTTP方法）
     * 参数: prefix, path
     */
    public static final String TEMPLATE_KEY_PATH = "%s:%s";

    /**
     * Key 模板：路径模式策略（含HTTP方法）
     * 参数: prefix, pattern, httpMethod
     */
    public static final String TEMPLATE_KEY_PATH_PATTERN_WITH_METHOD = "%s:%s:%s";

    /**
     * Key 模板：路径模式策略（不含HTTP方法）
     * 参数: prefix, pattern
     */
    public static final String TEMPLATE_KEY_PATH_PATTERN = "%s:%s";

    /**
     * ZSET member 模板：滑动窗口
     * 参数: threadId, nanoTime
     */
    public static final String TEMPLATE_SLIDING_WINDOW_MEMBER = "m-%d-%d";

    /**
     * 限流规则序列化分隔符
     */
    public static final String RULE_SEPARATOR = ",";

    /**
     * 限流规则序列化格式
     * 参数: count, window, unit
     */
    public static final String TEMPLATE_RULE_FORMAT = "%d/%d%s";

    /**
     * 默认排除路径模式
     */
    public static final String[] DEFAULT_EXCLUDE_PATTERNS = {"/actuator/**", "/actuator", "/health", "/health/**"};

    // ==================== 时间换算 ====================

    /**
     * 毫秒转秒的除数
     */
    public static final long MILLIS_PER_SECOND = 1000L;

    // ==================== 兼容错误码 ====================

    /**
     * 错误码：限流超限
     *
     * @deprecated 使用 {@link ErrorCode#RATE_LIMIT_EXCEEDED}
     */
    @Deprecated
    public static final String ERROR_CODE_RATE_LIMIT_EXCEEDED = ErrorCode.RATE_LIMIT_EXCEEDED;

    // ==================== 兼容异常消息 ====================

    /**
     * 消息：未找到 KeyGenerator
     *
     * @deprecated 使用 {@link ErrorMessage#KEY_GENERATOR_NOT_FOUND}
     */
    @Deprecated
    public static final String MSG_KEY_GENERATOR_NOT_FOUND = ErrorMessage.KEY_GENERATOR_NOT_FOUND_PREFIX;

    /**
     * 消息：ClientIp 不能为 null
     *
     * @deprecated 使用 {@link ErrorMessage#CLIENT_IP_NULL}
     */
    @Deprecated
    public static final String MSG_CLIENT_IP_NULL = ErrorMessage.CLIENT_IP_NULL;

    /**
     * 消息：RequestPath 不能为 null
     *
     * @deprecated 使用 {@link ErrorMessage#REQUEST_PATH_NULL}
     */
    @Deprecated
    public static final String MSG_REQUEST_PATH_NULL = ErrorMessage.REQUEST_PATH_NULL;

    /**
     * 消息：Method 不能为 null
     *
     * @deprecated 使用 {@link ErrorMessage#METHOD_NULL}
     */
    @Deprecated
    public static final String MSG_METHOD_NULL = ErrorMessage.METHOD_NULL;

    /**
     * 消息：RequestPath 和 MatchedPathPattern 都为 null
     *
     * @deprecated 使用 {@link ErrorMessage#PATH_PATTERN_NULL}
     */
    @Deprecated
    public static final String MSG_PATH_PATTERN_NULL = ErrorMessage.PATH_PATTERN_NULL;

    /**
     * 消息：限流触发
     *
     * @deprecated 使用 {@link ErrorMessage#RATE_LIMIT_EXCEEDED}
     */
    @Deprecated
    public static final String MSG_RATE_LIMIT_EXCEEDED = ErrorMessage.RATE_LIMIT_EXCEEDED;
}
