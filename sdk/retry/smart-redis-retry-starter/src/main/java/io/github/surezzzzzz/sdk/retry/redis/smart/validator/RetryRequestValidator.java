package io.github.surezzzzzz.sdk.retry.redis.smart.validator;

/**
 * 重试请求校验器
 *
 * @author surezzzzzz
 */
public interface RetryRequestValidator<T> {

    /**
     * 判断是否支持当前请求类型
     *
     * @param requestType 请求类型
     * @return 是否支持
     */
    boolean supports(Class<?> requestType);

    /**
     * 校验请求
     *
     * @param request 请求对象
     */
    void validate(T request);
}
