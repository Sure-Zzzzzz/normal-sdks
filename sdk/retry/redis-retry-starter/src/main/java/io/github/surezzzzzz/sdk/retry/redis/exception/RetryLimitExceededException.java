package io.github.surezzzzzz.sdk.retry.redis.exception;

import io.github.surezzzzzz.sdk.retry.redis.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.constant.ErrorMessage;
import lombok.Getter;

/**
 * 重试次数超限异常
 *
 * @author surezzzzzz
 */
@Getter
public class RetryLimitExceededException extends RedisRetryException {

    private static final long serialVersionUID = 1L;

    private final int currentCount;
    private final int maxCount;

    public RetryLimitExceededException(int currentCount, int maxCount) {
        super(ErrorCode.RETRY_OPERATION_FAILED, String.format(ErrorMessage.RETRY_LIMIT_EXCEEDED, currentCount, maxCount));
        this.currentCount = currentCount;
        this.maxCount = maxCount;
    }
}
