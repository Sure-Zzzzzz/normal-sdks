package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

/**
 * SmartRedisLimiter 基础异常类
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SmartRedisLimiterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SmartRedisLimiterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
