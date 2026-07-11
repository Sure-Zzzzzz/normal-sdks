package io.github.surezzzzzz.sdk.retry.redis.constant;

/**
 * 错误码常量
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String RETRY_OPERATION_FAILED = "BIZ_001";
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_001";
}
