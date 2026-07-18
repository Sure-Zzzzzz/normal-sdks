package io.github.surezzzzzz.sdk.lock.redis.constant;

/**
 * Simple Redis Lock 常量
 *
 * @author surezzzzzz
 */
public final class SimpleRedisLockConstant {

    public static final String UTILITY_CLASS_ERROR_MESSAGE = "Utility class";
    public static final long MIN_LEASE_MILLIS = 1L;

    private SimpleRedisLockConstant() {
        throw new UnsupportedOperationException(UTILITY_CLASS_ERROR_MESSAGE);
    }

    // ==================== 配置相关常量 ====================

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.lock.redis";
    public static final String ROUTE_CONFIG_PREFIX = CONFIG_PREFIX + ".route";
    public static final String PROPERTY_ENABLE = "enable";
    public static final String PROPERTY_VALUE_TRUE = "true";
    public static final String PROPERTY_VALUE_FALSE = "false";

    public static final boolean DEFAULT_ROUTE_ENABLE = false;

    // ==================== Redis 脚本常量 ====================

    public static final Long REDIS_SCRIPT_SUCCESS_RESULT = 1L;
    public static final String REDIS_UNLOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) "
            + "else "
            + "return 0 "
            + "end";
    public static final String REDIS_RENEW_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('pexpire', KEYS[1], ARGV[2]) "
            + "else "
            + "return 0 "
            + "end";

    // ==================== Bean 名称常量 ====================

    public static final String SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME = "simpleRedisLockRedisTemplate";

    // ==================== 条件类名常量 ====================

    public static final String REDIS_ROUTE_TEMPLATE_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate";
    public static final String SIMPLE_REDIS_ROUTE_CONFIGURATION_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration";
}
