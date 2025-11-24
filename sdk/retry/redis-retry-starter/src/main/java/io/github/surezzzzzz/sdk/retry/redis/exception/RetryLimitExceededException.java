package io.github.surezzzzzz.sdk.retry.redis.exception;

import lombok.Getter;

/**
 * 重试次数超限异常
 */
@Getter
public class RetryLimitExceededException extends RedisRetryException {
    private final int currentCount;
    private final int maxCount;

    public RetryLimitExceededException(int currentCount, int maxCount) {
        super(String.format("重试次数已超限: %d/%d", currentCount, maxCount));
        this.currentCount = currentCount;
        this.maxCount = maxCount;
    }

}
