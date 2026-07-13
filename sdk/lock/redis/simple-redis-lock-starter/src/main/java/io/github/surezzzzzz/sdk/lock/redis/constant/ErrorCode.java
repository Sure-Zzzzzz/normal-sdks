package io.github.surezzzzzz.sdk.lock.redis.constant;

/**
 * 错误码常量
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================

    public static final String CONFIG_MISSING_REDIS_ROUTE_TEMPLATE = "CONFIG_001";
}
