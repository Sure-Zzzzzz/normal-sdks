package io.github.surezzzzzz.sdk.audit.limiter.management.handler.impl;

import io.github.surezzzzzz.sdk.audit.limiter.management.annotation.SmartRedisLimiterManagementAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.management.constant.SmartRedisLimiterManagementAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.limiter.management.handler.SmartRedisLimiterManagementAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterManagementAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志三元组管理审计处理器
 *
 * <p>仅输出动作事实字段：策略键三元组与操作人为可读原文（操作人是资源上下文
 * 的已验证稳定标识）；不输出策略窗口数值快照，不反查身份系统。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterManagementAuditListenerComponent
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementAuditListenerConstant.LOG_HANDLER_CONFIG_PREFIX,
        name = "management-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LogSmartRedisLimiterManagementAuditHandler implements SmartRedisLimiterManagementAuditHandler {

    @Override
    public void handle(SmartRedisLimiterManagementAuditRecord record) {
        log.info("[LimiterManagementAudit] operation={}, serviceCode={}, resourceCode={}, subject={}, "
                        + "beforeEnabled={}, afterEnabled={}, revision={}, operator={}, occurredAt={}",
                record.getOperation(), record.getServiceCode(), record.getResourceCode(),
                record.getSubject(), record.getBeforeEnabled(), record.getAfterEnabled(),
                record.getRevision(), record.getOperator(), record.getOccurredAt());
    }
}
