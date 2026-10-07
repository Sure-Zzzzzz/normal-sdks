package io.github.surezzzzzz.sdk.audit.limiter.handler;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;

/**
 * SmartRedisLimiter 限流审计 Handler
 *
 * <p>事件监听器将限流事件转换为 {@link SmartRedisLimiterRecord} 后，
 * 遍历调用所有已注册的 Handler 进行处理。
 *
 * <p>SDK 提供默认日志实现 {@link io.github.surezzzzzz.sdk.audit.limiter.handler.impl.LogSmartRedisLimiterAuditHandler}。
 * 业务方可注册自己的 Handler 实现持久化到数据库、ES 等。
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterAuditHandler {

    /**
     * 处理限流审计记录
     *
     * @param record 限流审计记录
     */
    void handle(SmartRedisLimiterRecord record);

    /**
     * Handler 名称（用于日志）
     *
     * @return Handler 名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
