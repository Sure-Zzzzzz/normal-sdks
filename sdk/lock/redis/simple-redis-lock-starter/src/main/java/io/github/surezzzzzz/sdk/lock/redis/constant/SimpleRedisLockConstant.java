package io.github.surezzzzzz.sdk.lock.redis.constant;

/**
 * Simple Redis Lock 常量
 *
 * @author surezzzzzz
 */
public final class SimpleRedisLockConstant {

    private SimpleRedisLockConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关常量 ====================

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.lock.redis";
    public static final String ROUTE_CONFIG_PREFIX = CONFIG_PREFIX + ".route";
    public static final String PROPERTY_ENABLE = "enable";
    public static final String PROPERTY_VALUE_TRUE = "true";
    public static final String PROPERTY_VALUE_FALSE = "false";

    public static final boolean DEFAULT_ROUTE_ENABLE = false;

    // ==================== Bean 名称常量 ====================

    public static final String SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME = "simpleRedisLockRedisTemplate";

    // ==================== 条件类名常量 ====================

    public static final String REDIS_ROUTE_TEMPLATE_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate";
    public static final String SIMPLE_REDIS_ROUTE_CONFIGURATION_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration";
}
