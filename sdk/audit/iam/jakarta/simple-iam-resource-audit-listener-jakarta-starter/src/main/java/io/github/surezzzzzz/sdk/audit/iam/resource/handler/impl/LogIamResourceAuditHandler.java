package io.github.surezzzzzz.sdk.audit.iam.resource.handler.impl;

import io.github.surezzzzzz.sdk.audit.iam.resource.annotation.SimpleIamResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.iam.resource.handler.IamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志版 IAM 资源访问审计处理器。
 *
 * <p>默认实现：将审计记录以结构化形式输出到日志，可被业务方自定义 handler 替换或并存。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamResourceAuditListenerComponent
public class LogIamResourceAuditHandler implements IamResourceAuditHandler {

    /**
     * 输出一条资源访问审计日志。
     *
     * @param record 审计记录
     */
    @Override
    public void handle(IamResourceAuditRecord record) {
        log.info("IAM资源访问审计: sourceId={}, subjectType={}, subjectId={}, applicationCode={}, "
                        + "requestId={}, uri={}, method={}, remoteAddr={}, userAgent={}, timestamp={}, traceId={}",
                record.getAuthenticationSourceId(), record.getSubjectType(), record.getSubjectId(),
                record.getApplicationCode(), record.getRequestId(), record.getRequestUri(),
                record.getHttpMethod(), record.getRemoteAddr(), record.getUserAgent(),
                record.getTimestamp(), record.getTraceId());
    }
}
