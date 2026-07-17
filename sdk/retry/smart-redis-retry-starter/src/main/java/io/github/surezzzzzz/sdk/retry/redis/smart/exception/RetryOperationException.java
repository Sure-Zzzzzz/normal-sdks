package io.github.surezzzzzz.sdk.retry.redis.smart.exception;

/**
 * Smart Redis Retry 操作异常
 *
 * @author surezzzzzz
 */
public class RetryOperationException extends SmartRedisRetryException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /**
     * 创建不带根因的操作异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public RetryOperationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建带根因的操作异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 异常根因
     */
    public RetryOperationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
