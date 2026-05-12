package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

import lombok.Getter;

/**
 * SmartRedisLimiter 基础异常类
 *
 * @author surezzzzzz
 */
@Getter
public class SmartRedisLimiterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 构造异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public SmartRedisLimiterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原始异常
     */
    public SmartRedisLimiterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
