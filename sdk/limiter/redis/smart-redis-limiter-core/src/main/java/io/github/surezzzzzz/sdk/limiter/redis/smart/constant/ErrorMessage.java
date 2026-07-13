package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter 错误消息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================

    /**
     * RedisRouteTemplate 缺失
     */
    public static final String CONFIG_REDIS_ROUTE_TEMPLATE_MISSING =
            "RedisRouteTemplate 不存在，请确认 simple-redis-route-starter 已引入且 io.github.surezzzzzz.sdk.redis.route.enable=true";

    // ==================== 路由错误 ====================

    /**
     * Redis Route 执行失败
     */
    public static final String ROUTE_EXECUTION_FAILED = "Redis Route 执行失败：%s";

    // ==================== Key 生成错误 ====================

    /**
     * KeyProvider 执行失败
     */
    public static final String KEY_PROVIDER_ERROR = "SmartRedisLimiter KeyProvider 执行失败：%s";

    /**
     * KeyGenerator 未找到
     */
    public static final String KEY_GENERATOR_NOT_FOUND = "未找到 KeyGenerator：%s";

    /**
     * KeyGenerator 未找到消息前缀，兼容 1.x 常量拼接用法
     */
    public static final String KEY_GENERATOR_NOT_FOUND_PREFIX = "未找到KeyGenerator: ";

    /**
     * keyPart 为空
     */
    public static final String KEY_PART_EMPTY = "keyPart 不能为空";

    /**
     * ClientIp 为空
     */
    public static final String CLIENT_IP_NULL = "ClientIp不能为null";

    /**
     * RequestPath 为空
     */
    public static final String REQUEST_PATH_NULL = "RequestPath不能为null";

    /**
     * Method 为空
     */
    public static final String METHOD_NULL = "Method不能为null";

    /**
     * RequestPath 和 MatchedPathPattern 都为空
     */
    public static final String PATH_PATTERN_NULL = "RequestPath和MatchedPathPattern都为null";

    // ==================== 限流错误 ====================

    /**
     * 限流超限
     */
    public static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded for key: %s, retry after %d seconds";
}
