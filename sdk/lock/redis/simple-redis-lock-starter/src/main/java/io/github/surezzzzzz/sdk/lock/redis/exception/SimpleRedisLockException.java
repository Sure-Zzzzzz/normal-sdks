package io.github.surezzzzzz.sdk.lock.redis.exception;

import lombok.Getter;

/**
 * Simple Redis Lock 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleRedisLockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleRedisLockException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleRedisLockException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
