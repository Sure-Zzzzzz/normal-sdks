package io.github.surezzzzzz.sdk.retry.redis.smart.exception;

import lombok.Getter;

/**
 * Smart Redis Retry 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class SmartRedisRetryException extends RuntimeException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;
    /** 错误码 */
    private final String errorCode;

    /**
     * 创建不带根因的基础异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public SmartRedisRetryException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建带根因的基础异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 异常根因
     */
    public SmartRedisRetryException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
