package io.github.surezzzzzz.sdk.audit.limiter.management.support;

import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterManagementAuditRecord;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;

/**
 * 三元组限流策略管理审计记录转换工具
 *
 * <p>只提取动作事实字段，不读取 beforePolicy/afterPolicy 的窗口数值；
 * 操作人取事件自身携带的已验证稳定标识，不从审计侧反查任何身份系统。</p>
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterManagementAuditRecordHelper {

    /**
     * 不反查身份系统：审计只消费事件自身字段
     */
    public SmartRedisLimiterManagementAuditRecordHelper() {
    }

    /**
     * 将三元组管理事件载荷转换为动作事实审计记录
     *
     * @param payload 三元组管理事件载荷
     * @return 动作事实审计记录
     */
    public SmartRedisLimiterManagementAuditRecord map(SmartRedisLimiterManagementEventPayload payload) {
        return SmartRedisLimiterManagementAuditRecord.builder()
                .operation(payload.getOperation() == null ? null : payload.getOperation().getCode())
                .serviceCode(payload.getPolicyKey() == null ? null : payload.getPolicyKey().getServiceCode())
                .resourceCode(payload.getPolicyKey() == null ? null : payload.getPolicyKey().getResourceCode())
                .subject(payload.getPolicyKey() == null ? null : payload.getPolicyKey().getSubject())
                .beforeEnabled(payload.getBeforeEnabled())
                .afterEnabled(payload.getAfterEnabled())
                .revision(payload.getRevision())
                .operator(payload.getOperator())
                .occurredAt(payload.getOccurredAt())
                .build();
    }
}
