package io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception;

/**
 * 限流策略冲突异常
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterPolicyConflictException extends SmartRedisLimiterManagementException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造策略冲突异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public SmartRedisLimiterPolicyConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
