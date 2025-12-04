package io.github.surezzzzzz.sdk.limiter.redis.exception;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/3/27 17:33
 */
public class RedisLimiterException extends RuntimeException{
    public RedisLimiterException(String message) {
        super(message);
    }
}