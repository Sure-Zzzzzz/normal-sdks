package io.github.surezzzzzz.sdk.retry.redis.smart.validator;

import io.github.surezzzzzz.sdk.retry.redis.smart.annotation.SmartRedisRetryComponent;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryValidationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;

/**
 * 重试策略校验器
 *
 * @author surezzzzzz
 */
@SmartRedisRetryComponent
public class RetryPolicyValidator implements RetryRequestValidator<RetryPolicy> {

    /**
     * 判断是否支持重试策略。
     *
     * @param requestType 请求类型
     * @return true 表示支持，false 表示不支持
     */
    @Override
    public boolean supports(Class<?> requestType) {
        return RetryPolicy.class.isAssignableFrom(requestType);
    }

    /**
     * 校验重试策略参数。
     *
     * @param policy 重试策略
     */
    @Override
    public void validate(RetryPolicy policy) {
        if (policy == null) {
            throw new RetryValidationException(ErrorCode.RETRY_POLICY_INVALID, ErrorMessage.RETRY_POLICY_INVALID);
        }
        if (policy.getMaxRetryTimes() == null
                || policy.getMaxRetryTimes() < SmartRedisRetryConstant.RETRY_COUNT_INITIAL
                || policy.getMaxRetryTimes() > SmartRedisRetryConstant.MAX_RETRY_TIMES) {
            throw new RetryValidationException(ErrorCode.RETRY_POLICY_INVALID, ErrorMessage.RETRY_POLICY_INVALID);
        }
        if (policy.getRetryIntervalMillis() == null
                || policy.getRetryIntervalMillis() < SmartRedisRetryConstant.LONG_ZERO
                || policy.getRetryIntervalMillis() > SmartRedisRetryConstant.MAX_RETRY_INTERVAL_MILLIS) {
            throw new RetryValidationException(ErrorCode.RETRY_POLICY_INVALID, ErrorMessage.RETRY_POLICY_INVALID);
        }
        if (policy.getMaxIntervalMillis() == null
                || policy.getMaxIntervalMillis() < policy.getRetryIntervalMillis()
                || policy.getMaxIntervalMillis() > SmartRedisRetryConstant.MAX_RETRY_INTERVAL_MILLIS) {
            throw new RetryValidationException(ErrorCode.RETRY_POLICY_INVALID, ErrorMessage.RETRY_POLICY_INVALID);
        }
        if (policy.getBackoffMultiplier() == null
                || Double.isNaN(policy.getBackoffMultiplier())
                || Double.isInfinite(policy.getBackoffMultiplier())
                || policy.getBackoffMultiplier() < SmartRedisRetryConstant.MIN_BACKOFF_MULTIPLIER
                || policy.getBackoffMultiplier() > SmartRedisRetryConstant.MAX_BACKOFF_MULTIPLIER) {
            throw new RetryValidationException(ErrorCode.RETRY_POLICY_INVALID, ErrorMessage.RETRY_POLICY_INVALID);
        }
        if (policy.getJitterRatio() == null
                || Double.isNaN(policy.getJitterRatio())
                || Double.isInfinite(policy.getJitterRatio())
                || policy.getJitterRatio() < SmartRedisRetryConstant.DOUBLE_ZERO
                || policy.getJitterRatio() > SmartRedisRetryConstant.MAX_JITTER_RATIO) {
            throw new RetryValidationException(ErrorCode.RETRY_POLICY_INVALID, ErrorMessage.RETRY_POLICY_INVALID);
        }
    }
}
