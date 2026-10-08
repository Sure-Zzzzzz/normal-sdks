package io.github.surezzzzzz.sdk.audit.limiter.management.handler;

import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterManagementAuditRecord;

/**
 * 三元组限流策略管理审计 Handler
 *
 * <p>监听器将三元组管理事件转换为 {@link SmartRedisLimiterManagementAuditRecord} 后，
 * 遍历调用所有已注册的 Handler 处理。记录只含动作事实字段，Handler 不得回溯原始策略内容。</p>
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterManagementAuditHandler {

    /**
     * 处理三元组管理审计记录
     *
     * @param record 三元组管理审计记录
     */
    void handle(SmartRedisLimiterManagementAuditRecord record);

    /**
     * Handler 名称（用于日志）
     *
     * @return Handler 名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
