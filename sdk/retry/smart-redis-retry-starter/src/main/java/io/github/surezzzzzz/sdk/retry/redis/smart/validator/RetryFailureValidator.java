package io.github.surezzzzzz.sdk.retry.redis.smart.validator;

import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryValidationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import lombok.RequiredArgsConstructor;

/**
 * 重试失败请求校验器
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class RetryFailureValidator implements RetryRequestValidator<RetryFailure> {

    /**
     * Smart Redis Retry 配置
     */
    private final SmartRedisRetryProperties properties;
    /**
     * 重试策略校验器
     */
    private final RetryPolicyValidator retryPolicyValidator;

    /**
     * 判断是否支持失败请求。
     *
     * @param requestType 请求类型
     * @return true 表示支持，false 表示不支持
     */
    @Override
    public boolean supports(Class<?> requestType) {
        return RetryFailure.class.isAssignableFrom(requestType);
    }

    /**
     * 校验失败请求。
     *
     * @param failure 失败请求
     */
    @Override
    public void validate(RetryFailure failure) {
        if (!hasText(failure.getRetryType())) {
            throw new RetryValidationException(ErrorCode.RETRY_TYPE_EMPTY, ErrorMessage.RETRY_TYPE_EMPTY);
        }
        if (!hasText(failure.getRetryKey())) {
            throw new RetryValidationException(ErrorCode.RETRY_KEY_EMPTY, ErrorMessage.RETRY_KEY_EMPTY);
        }
        if (failure.getRetryKey().length() > properties.getGuard().getMaxRetryKeyLength()) {
            throw new RetryValidationException(ErrorCode.RETRY_KEY_TOO_LONG,
                    String.format(ErrorMessage.RETRY_KEY_TOO_LONG,
                            properties.getGuard().getMaxRetryKeyLength()));
        }
        if (failure.getPolicy() != null) {
            retryPolicyValidator.validate(failure.getPolicy());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
