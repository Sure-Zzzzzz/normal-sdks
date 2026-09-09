package io.github.surezzzzzz.sdk.audit.iam.server.handler.impl;

import io.github.surezzzzzz.sdk.audit.iam.server.annotation.SimpleIamServerAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.iam.server.handler.ServerIamAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.server.model.ServerIamAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志 IAM 审计处理器
 *
 * <p>仅输出事件族、动作类型及非敏感主体摘要，不输出完整审计记录或 Token 原文。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleIamServerAuditListenerComponent
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.audit.iam.server.listener.handler.log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LogServerIamAuditHandler implements ServerIamAuditHandler {

    @Override
    public void handle(ServerIamAuditRecord record) {
        log.info("IAM_AUDIT family={}, eventType={}, cause={}, userId={}, username={}, clientId={}, "
                        + "subjectType={}, subjectId={}, operator={}, errorCode={}, revokedCount={}, active={}",
                record.getFamily(), record.getEventType(), record.getCause(), record.getUserId(),
                record.getUsername(), record.getClientId(), record.getSubjectType(), record.getSubjectId(),
                record.getOperator(), record.getErrorCode(), record.getRevokedCount(), record.getActive());
    }
}
