package io.github.surezzzzzz.sdk.retry.redis.smart.exception;

/**
 * Smart Redis Retry 校验异常
 *
 * @author surezzzzzz
 */
public class RetryValidationException extends SmartRedisRetryException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /**
     * 创建不带根因的校验异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public RetryValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建带根因的校验异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 异常根因
     */
    public RetryValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
