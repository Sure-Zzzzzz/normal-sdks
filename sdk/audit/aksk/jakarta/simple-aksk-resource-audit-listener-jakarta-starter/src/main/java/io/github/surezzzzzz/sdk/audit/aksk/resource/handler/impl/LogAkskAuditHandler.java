package io.github.surezzzzzz.sdk.audit.aksk.resource.handler.impl;

import io.github.surezzzzzz.sdk.audit.aksk.resource.annotation.SimpleAkskResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.resource.constant.AkskResourceAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认关闭的日志处理器；仅输出关联摘要，不打印全量记录、地址或客户端标识。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskResourceAuditListenerComponent
@ConditionalOnProperty(prefix = AkskResourceAuditListenerConstant.LOG_CONFIG_PREFIX,
        name = "enabled", havingValue = "true")
public class LogAkskAuditHandler implements AkskAuditHandler {
    /**
     * 输出最小审计摘要，不代表记录已持久化。
     *
     * @param record 当前访问的审计记录
     */
    @Override
    public void handle(AkskAuditRecord record) {
        log.info("AKSK资源访问审计: requestId={}, subjectType={}, method={}, timestamp={}",
                record.getRequestId(), record.getSubjectType(), record.getHttpMethod(), record.getTimestamp());
    }
}
