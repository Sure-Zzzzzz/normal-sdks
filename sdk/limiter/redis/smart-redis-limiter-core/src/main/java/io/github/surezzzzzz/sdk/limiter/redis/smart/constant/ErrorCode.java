package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter 错误码常量
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================

    /**
     * RedisRouteTemplate 缺失
     */
    public static final String CONFIG_REDIS_ROUTE_TEMPLATE_MISSING = "CONFIG_001";

    // ==================== 路由错误 ====================

    /**
     * Redis Route 执行失败
     */
    public static final String ROUTE_EXECUTION_FAILED = "ROUTE_001";

    // ==================== Key 生成错误 ====================

    /**
     * KeyProvider 执行失败
     */
    public static final String KEY_PROVIDER_ERROR = "KEY_001";

    /**
     * KeyGenerator 未找到
     */
    public static final String KEY_GENERATOR_NOT_FOUND = "KEY_002";

    // ==================== 限流错误 ====================

    /**
     * 限流超限
     */
    public static final String RATE_LIMIT_EXCEEDED = "BIZ_001";
}
