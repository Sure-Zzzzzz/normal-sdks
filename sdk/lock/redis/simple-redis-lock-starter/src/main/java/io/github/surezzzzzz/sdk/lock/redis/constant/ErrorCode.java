package io.github.surezzzzzz.sdk.lock.redis.constant;

/**
 * 错误码常量
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleRedisLockConstant.UTILITY_CLASS_ERROR_MESSAGE);
    }

    // ==================== 配置错误 ====================

    public static final String CONFIG_MISSING_REDIS_ROUTE_TEMPLATE = "CONFIG_001";

    // ==================== 参数校验错误 ====================

    public static final String VALIDATION_LEASE_TIME_UNIT_REQUIRED = "VALIDATION_001";
    public static final String VALIDATION_LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND = "VALIDATION_002";
}
