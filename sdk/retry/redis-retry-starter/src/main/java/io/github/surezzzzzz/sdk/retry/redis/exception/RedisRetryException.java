package io.github.surezzzzzz.sdk.retry.redis.exception;

import io.github.surezzzzzz.sdk.retry.redis.constant.ErrorCode;
import lombok.Getter;

/**
 * Redis 重试异常基类
 *
 * @author surezzzzzz
 */
@Getter
public class RedisRetryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public RedisRetryException(String message) {
        super(message);
        this.errorCode = ErrorCode.RETRY_OPERATION_FAILED;
    }

    public RedisRetryException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.RETRY_OPERATION_FAILED;
    }

    public RedisRetryException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RedisRetryException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
