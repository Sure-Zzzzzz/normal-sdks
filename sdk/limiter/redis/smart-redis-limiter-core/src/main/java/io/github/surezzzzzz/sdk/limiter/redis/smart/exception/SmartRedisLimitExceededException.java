package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
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

    public SmartRedisLimitExceededException(String key, long retryAfter) {
        super(SmartRedisLimiterConstant.ERROR_CODE_RATE_LIMIT_EXCEEDED, String.format(SmartRedisLimiterConstant.MSG_RATE_LIMIT_EXCEEDED, key, retryAfter));
        this.key = key;
        this.retryAfter = retryAfter;
    }
}
