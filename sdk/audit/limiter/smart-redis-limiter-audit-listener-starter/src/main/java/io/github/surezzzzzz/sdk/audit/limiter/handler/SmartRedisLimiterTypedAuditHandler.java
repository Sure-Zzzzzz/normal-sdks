package io.github.surezzzzzz.sdk.audit.limiter.handler;

import io.github.surezzzzzz.sdk.audit.limiter.model.SmartRedisLimiterTypedAuditRecord;

/**
 * 类型化限流策略管理审计 Handler
 *
 * <p>监听器将类型化管理事件转换为 {@link SmartRedisLimiterTypedAuditRecord} 后，
 * 遍历调用所有已注册的 Handler 处理。记录只含受控摘要字段，Handler 不得回溯原始对象。</p>
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterTypedAuditHandler {

    /**
     * 处理类型化管理审计记录
     *
     * @param record 类型化管理审计记录
     */
    void handle(SmartRedisLimiterTypedAuditRecord record);

    /**
     * Handler 名称（用于日志）
     *
     * @return Handler 名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
