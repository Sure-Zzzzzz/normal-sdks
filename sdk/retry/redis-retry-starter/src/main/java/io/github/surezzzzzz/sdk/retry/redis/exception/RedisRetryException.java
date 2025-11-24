package io.github.surezzzzzz.sdk.retry.redis.exception;

/**
 * Redis 重试异常基类
 */
public class RedisRetryException extends RuntimeException {
    public RedisRetryException(String message) {
        super(message);
    }

    public RedisRetryException(String message, Throwable cause) {
        super(message, cause);
    }
}