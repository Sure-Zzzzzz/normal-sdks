package io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;

/**
 * 类型化策略服务未装配（宿主缺少 JDBC 数据源装配）时抛出，映射服务不可用
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterTypedServiceUnavailableException extends SmartRedisLimiterManagementException {

    /**
     * 构造服务不可用异常
     */
    public SmartRedisLimiterTypedServiceUnavailableException() {
        super(ErrorCode.TYPED_SERVICE_UNAVAILABLE, ErrorMessage.TYPED_SERVICE_UNAVAILABLE);
    }
}
