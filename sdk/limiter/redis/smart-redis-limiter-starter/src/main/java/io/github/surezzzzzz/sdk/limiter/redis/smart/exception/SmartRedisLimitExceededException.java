package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

import lombok.Getter;

/**
 * @author: Sure.
 * @description 智能限流超限异常
 * @Date: 2024/12/XX XX:XX
 */
@Getter
public class SmartRedisLimitExceededException extends RuntimeException {

    /**
     * 限流Key
     */
    private final String key;

    /**
     * 重试等待时间（秒）
     */
    private final long retryAfter;

    public SmartRedisLimitExceededException(String key, long retryAfter) {
        super(String.format("Rate limit exceeded for key: %s, retry after %d seconds", key, retryAfter));
        this.key = key;
        this.retryAfter = retryAfter;
    }
}
