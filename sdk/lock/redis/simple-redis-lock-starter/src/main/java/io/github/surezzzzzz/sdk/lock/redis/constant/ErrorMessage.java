package io.github.surezzzzzz.sdk.lock.redis.constant;

/**
 * 错误消息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleRedisLockConstant.UTILITY_CLASS_ERROR_MESSAGE);
    }

    // ==================== 配置错误 ====================

    public static final String CONFIG_MISSING_REDIS_ROUTE_TEMPLATE =
            "已开启 lock route（io.github.surezzzzzz.sdk.lock.redis.route.enable=true），但未找到 RedisRouteTemplate Bean。请确认已引入并启用 simple-redis-route-starter。";

    // ==================== 执行器错误 ====================

    public static final String EXECUTOR_UNSUPPORTED_LEASE_RENEW = "当前 RedisLockExecutor 不支持租约续租";

    // ==================== 参数校验错误 ====================

    public static final String LEASE_TIME_UNIT_REQUIRED = "租约时间单位不能为空";
    public static final String LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND = "租约时长必须至少为 1 毫秒";
}
