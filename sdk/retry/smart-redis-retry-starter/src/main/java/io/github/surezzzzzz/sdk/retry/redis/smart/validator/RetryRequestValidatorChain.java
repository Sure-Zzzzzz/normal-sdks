package io.github.surezzzzzz.sdk.retry.redis.smart.validator;

import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 重试请求校验链
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class RetryRequestValidatorChain {

    /**
     * 全部请求校验器
     */
    private final List<RetryRequestValidator<?>> validators;

    /**
     * 使用匹配的校验器校验请求。
     *
     * @param request 待校验请求
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void validate(Object request) {
        if (request == null || validators == null) {
            return;
        }
        for (RetryRequestValidator validator : validators) {
            if (validator.supports(request.getClass())) {
                validator.validate(request);
            }
        }
    }
}
