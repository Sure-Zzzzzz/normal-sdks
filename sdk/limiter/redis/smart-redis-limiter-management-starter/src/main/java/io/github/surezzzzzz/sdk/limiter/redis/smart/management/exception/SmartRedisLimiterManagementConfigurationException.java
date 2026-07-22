package io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception;

/**
 * SmartRedisLimiter Management 配置异常
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterManagementConfigurationException extends SmartRedisLimiterManagementException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造配置异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public SmartRedisLimiterManagementConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
