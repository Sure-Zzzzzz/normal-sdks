package io.github.surezzzzzz.sdk.audit.iam.resource.handler.impl;

import io.github.surezzzzzz.sdk.audit.iam.resource.annotation.SimpleIamResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.iam.resource.handler.IamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志审计处理器
 *
 * <p>默认关闭；启用后按 INFO 级别逐字段输出审计摘要，不输出 Token 原文或凭据。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleIamResourceAuditListenerComponent
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.audit.iam.resource.listener.handler.log",
        name = "enabled",
        havingValue = "true"
)
public class LogIamResourceAuditHandler implements IamResourceAuditHandler {

    /**
     * 输出审计记录到日志
     *
     * @param record 审计记录
     */
    @Override
    public void handle(IamResourceAuditRecord record) {
        log.info("IAM_RESOURCE_AUDIT sourceId={}, subjectType={}, subjectId={}, applicationCode={}, "
                        + "requestId={}, uri={}, method={}, remoteAddr={}, userAgent={}, timestamp={}, traceId={}",
                record.getAuthenticationSourceId(), record.getSubjectType(), record.getSubjectId(),
                record.getApplicationCode(), record.getRequestId(), record.getRequestUri(),
                record.getHttpMethod(), record.getRemoteAddr(), record.getUserAgent(),
                record.getTimestamp(), record.getTraceId());
    }
}
