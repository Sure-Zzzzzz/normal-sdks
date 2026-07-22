package io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;

/**
 * 限流策略不存在异常
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterPolicyNotFoundException extends SmartRedisLimiterManagementException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建策略不存在异常
     */
    public SmartRedisLimiterPolicyNotFoundException() {
        super(ErrorCode.POLICY_NOT_FOUND, ErrorMessage.POLICY_NOT_FOUND);
    }
}
