package io.github.surezzzzzz.sdk.audit.aksk.server.handler.impl;

import io.github.surezzzzzz.sdk.audit.aksk.server.annotation.SimpleAkskServerAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志 Token 审计处理器
 *
 * <p>将 Token 生命周期审计记录输出到日志。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleAkskServerAuditListenerComponent
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.audit.aksk.server.listener.handler.log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LogServerTokenAuditHandler implements ServerTokenAuditHandler {

    @Override
    public void handle(ServerTokenAuditRecord record) {
        log.info("AKSK_SERVER_AUDIT - {}", record);
    }
}
