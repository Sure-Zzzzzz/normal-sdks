package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

/**
 * SmartRedisLimiter Key 异常
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterKeyException extends SmartRedisLimiterException {

    private static final long serialVersionUID = 1L;

    public SmartRedisLimiterKeyException(String errorCode, String message) {
        super(errorCode, message);
    }

    public SmartRedisLimiterKeyException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
