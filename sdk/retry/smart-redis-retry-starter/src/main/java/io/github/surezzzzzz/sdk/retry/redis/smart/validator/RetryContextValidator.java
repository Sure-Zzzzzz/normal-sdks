package io.github.surezzzzzz.sdk.retry.redis.smart.validator;

import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryValidationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.RetryContextSerializer;
import lombok.RequiredArgsConstructor;

/**
 * 重试上下文校验器
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class RetryContextValidator implements RetryRequestValidator<RetryFailure> {

    /**
     * Smart Redis Retry 配置
     */
    private final SmartRedisRetryProperties properties;
    /**
     * 重试上下文序列化器
     */
    private final RetryContextSerializer serializer;

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
     * 校验序列化后的上下文长度。
     *
     * @param failure 失败请求
     */
    @Override
    public void validate(RetryFailure failure) {
        String contextJson = serializer.serialize(failure.getContext());
        if (contextJson != null
                && contextJson.length() > properties.getGuard().getMaxContextJsonLength()) {
            throw new RetryValidationException(ErrorCode.RETRY_CONTEXT_TOO_LONG,
                    String.format(ErrorMessage.RETRY_CONTEXT_TOO_LONG,
                            properties.getGuard().getMaxContextJsonLength()));
        }
    }
}
