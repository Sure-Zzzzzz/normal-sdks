package io.github.surezzzzzz.sdk.audit.limiter.management.handler.impl;

import io.github.surezzzzzz.sdk.audit.limiter.management.annotation.SmartRedisLimiterManagementAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.management.constant.SmartRedisLimiterManagementAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.limiter.management.handler.SmartRedisLimiterTypedAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterTypedAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志类型化管理审计处理器
 *
 * <p>仅输出受控摘要字段；不输出 operatorIdentity 完整身份事实、
 * 原始计数对象或扩展属性。操作人只记录短摘要。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterManagementAuditListenerComponent
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementAuditListenerConstant.LOG_HANDLER_CONFIG_PREFIX,
        name = "typed-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LogSmartRedisLimiterTypedAuditHandler implements SmartRedisLimiterTypedAuditHandler {

    @Override
    public void handle(SmartRedisLimiterTypedAuditRecord record) {
        if ("SUCCESS".equals(record.getResult())) {
            log.info("[LimiterTypedAudit:SUCCESS] operation={}, serviceCode={}, resourceCode={}, "
                            + "dimension={}, selector={}, ruleId={}, revision={}, objectDigest={}, "
                            + "operatorDigest={}, occurredAt={}",
                    record.getOperation(), record.getServiceCode(), record.getResourceCode(),
                    record.getDimension(), record.getSelector(), record.getRuleId(),
                    record.getRevision(), record.getObjectDigest(), record.getOperatorDigest(),
                    record.getOccurredAt());
        } else {
            log.warn("[LimiterTypedAudit:FAILURE] operation={}, serviceCode={}, resourceCode={}, "
                            + "dimension={}, selector={}, ruleId={}, revision={}, objectDigest={}, "
                            + "reason={}, operatorDigest={}, occurredAt={}",
                    record.getOperation(), record.getServiceCode(), record.getResourceCode(),
                    record.getDimension(), record.getSelector(), record.getRuleId(),
                    record.getRevision(), record.getObjectDigest(), record.getReason(),
                    record.getOperatorDigest(), record.getOccurredAt());
        }
    }
}
