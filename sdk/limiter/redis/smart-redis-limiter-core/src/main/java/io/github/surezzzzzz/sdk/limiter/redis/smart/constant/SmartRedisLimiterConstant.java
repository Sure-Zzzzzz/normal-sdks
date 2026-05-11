package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter 常量定义
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterConstant {

    // ==================== 来源标识 ====================

    /**
     * 来源：拦截器模式
     */
    String SOURCE_INTERCEPTOR = "INTERCEPTOR";

    /**
     * 来源：注解模式
     */
    String SOURCE_ASPECT = "ASPECT";

    // ==================== 注解 ====================

    /**
     * 注解全限定名
     */
    String ANNOTATION_CLASS_NAME = "io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter";

    // ==================== 缓存 Key ====================

    /**
     * 规则缓存Key分隔符
     */
    String CACHE_KEY_SEPARATOR = ":";

    // ==================== HTTP ====================

    /**
     * HTTP 429 状态码
     */
    int HTTP_STATUS_TOO_MANY_REQUESTS = 429;

    /**
     * HTTP 429 状态消息
     */
    String HTTP_MESSAGE_TOO_MANY_REQUESTS = "Too Many Requests";

    /**
     * Retry-After 响应头
     */
    String HEADER_RETRY_AFTER = "Retry-After";

    /**
     * X-RateLimit-Limit 响应头（时间窗口内的限流阈值）
     */
    String HEADER_X_RATELIMIT_LIMIT = "X-RateLimit-Limit";

    /**
     * X-RateLimit-Remaining 响应头（当前窗口剩余配额）
     */
    String HEADER_X_RATELIMIT_REMAINING = "X-RateLimit-Remaining";

    /**
     * X-RateLimit-Reset 响应头（窗口重置的 Unix 时间戳，秒）
     */
    String HEADER_X_RATELIMIT_RESET = "X-RateLimit-Reset";

    /**
     * X-Forwarded-For 请求头
     */
    String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * X-Real-IP 请求头
     */
    String HEADER_X_REAL_IP = "X-Real-IP";

    /**
     * IP 未知标识
     */
    String IP_UNKNOWN = "unknown";

    // ==================== 时间单位后缀 ====================

    /**
     * 秒后缀
     */
    String SUFFIX_SECONDS = "s";

    /**
     * 分后缀
     */
    String SUFFIX_MINUTES = "m";

    /**
     * 时后缀
     */
    String SUFFIX_HOURS = "h";

    /**
     * 天后缀
     */
    String SUFFIX_DAYS = "d";

    // ==================== 限流算法 ====================

    /**
     * 算法：固定窗口
     */
    String ALGORITHM_FIXED = "fixed";

    /**
     * 算法：滑动窗口
     */
    String ALGORITHM_SLIDING = "sliding";

    // ==================== 错误码 ====================

    /**
     * 错误码：限流超限
     */
    String ERROR_CODE_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";

    // ==================== 异常消息 ====================

    /**
     * 消息：未找到 KeyGenerator
     */
    String MSG_KEY_GENERATOR_NOT_FOUND = "未找到KeyGenerator: ";

    /**
     * 消息：ClientIp 不能为 null
     */
    String MSG_CLIENT_IP_NULL = "ClientIp不能为null";

    /**
     * 消息：RequestPath 不能为 null
     */
    String MSG_REQUEST_PATH_NULL = "RequestPath不能为null";

    /**
     * 消息：Method 不能为 null
     */
    String MSG_METHOD_NULL = "Method不能为null";

    /**
     * 消息：RequestPath 和 MatchedPathPattern 都为 null
     */
    String MSG_PATH_PATTERN_NULL = "RequestPath和MatchedPathPattern都为null";

    /**
     * 消息：限流触发
     */
    String MSG_RATE_LIMIT_EXCEEDED = "Rate limit exceeded for key: %s, retry after %d seconds";
}
