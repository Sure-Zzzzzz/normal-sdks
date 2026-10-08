package io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;

/**
 * 完整数据授权无法执行或目标超出授权范围。
 */
public class SmartRedisLimiterManagementAccessDeniedException extends SmartRedisLimiterManagementException {
    /**
     * 创建不暴露授权细节的访问拒绝。
     */
    public SmartRedisLimiterManagementAccessDeniedException() {
        super(ErrorCode.ACCESS_DENIED, ErrorMessage.ACCESS_DENIED);
    }
}
