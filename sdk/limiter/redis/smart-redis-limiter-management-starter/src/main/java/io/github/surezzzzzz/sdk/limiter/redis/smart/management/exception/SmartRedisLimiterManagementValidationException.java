package io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;

/**
 * Management 请求校验异常
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterManagementValidationException
        extends SmartRedisLimiterManagementException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建默认请求校验异常
     */
    public SmartRedisLimiterManagementValidationException() {
        this(String.format(ErrorMessage.POLICY_VALIDATION_FAILED, ErrorMessage.PAGE_INVALID));
    }

    /**
     * 创建请求校验异常
     *
     * @param message 面向调用方的校验提示
     */
    public SmartRedisLimiterManagementValidationException(String message) {
        super(ErrorCode.POLICY_VALIDATION_FAILED, message);
    }
}
