package io.github.surezzzzzz.sdk.limiter.redis.smart.management.event;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;

/**
 * 管理事件 commit 后发布扩展接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterManagementEventPublisher {

    /**
     * 注册事务提交后发布事件
     *
     * @param payload 管理事件载荷
     */
    void publishAfterCommit(SmartRedisLimiterManagementEventPayload payload);
}
