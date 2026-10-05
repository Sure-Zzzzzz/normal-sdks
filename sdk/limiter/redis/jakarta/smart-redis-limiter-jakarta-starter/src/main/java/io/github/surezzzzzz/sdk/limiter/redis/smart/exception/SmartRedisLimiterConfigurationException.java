package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

/**
 * SmartRedisLimiter 配置异常
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterConfigurationException extends SmartRedisLimiterException {

    private static final long serialVersionUID = 1L;

    public SmartRedisLimiterConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public SmartRedisLimiterConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
