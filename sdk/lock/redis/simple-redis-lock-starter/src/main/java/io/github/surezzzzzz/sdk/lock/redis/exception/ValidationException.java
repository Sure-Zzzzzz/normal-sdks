package io.github.surezzzzzz.sdk.lock.redis.exception;

/**
 * Simple Redis Lock 参数校验异常
 *
 * @author surezzzzzz
 */
public class ValidationException extends SimpleRedisLockException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
