package io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception;

import lombok.Getter;

/**
 * SmartRedisLimiter Management 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class SmartRedisLimiterManagementException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    /**
     * 构造管理异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public SmartRedisLimiterManagementException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造管理异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原始异常
     */
    public SmartRedisLimiterManagementException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
