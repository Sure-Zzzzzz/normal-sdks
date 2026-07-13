package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import lombok.Getter;

/**
 * SmartRedisLimiter 限流超限异常
 *
 * @author surezzzzzz
 */
@Getter
public class SmartRedisLimitExceededException extends SmartRedisLimiterException {

    private static final long serialVersionUID = 1L;

    /**
     * 限流Key
     */
    private final String key;

    /**
     * 重试等待时间（秒）
     */
    private final long retryAfter;

    /**
     * 限流阈值（时间窗口内允许的最大请求数）
     */
    private final long limit;

    /**
     * 剩余配额
     */
    private final long remaining;

    /**
     * 窗口重置的 Unix 时间戳（秒）
     */
    private final long resetAt;

    public SmartRedisLimitExceededException(String key, long retryAfter) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, String.format(ErrorMessage.RATE_LIMIT_EXCEEDED, key, retryAfter));
        this.key = key;
        this.retryAfter = retryAfter;
        this.limit = 0;
        this.remaining = 0;
        this.resetAt = 0;
    }

    public SmartRedisLimitExceededException(String key, long retryAfter, long limit, long remaining, long resetAt) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, String.format(ErrorMessage.RATE_LIMIT_EXCEEDED, key, retryAfter));
        this.key = key;
        this.retryAfter = retryAfter;
        this.limit = limit;
        this.remaining = remaining;
        this.resetAt = resetAt;
    }
}
