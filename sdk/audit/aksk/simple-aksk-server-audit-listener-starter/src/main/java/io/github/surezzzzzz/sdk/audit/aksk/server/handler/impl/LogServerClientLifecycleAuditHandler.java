package io.github.surezzzzzz.sdk.audit.aksk.server.handler.impl;

import io.github.surezzzzzz.sdk.audit.aksk.server.annotation.SimpleAkskServerAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerClientLifecycleAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerClientLifecycleAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志 AKU 生命周期审计处理器。
 */
@Slf4j
@SimpleAkskServerAuditListenerComponent
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.audit.aksk.server.listener.handler.log",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogServerClientLifecycleAuditHandler implements ServerClientLifecycleAuditHandler {

    @Override
    public void handle(ServerClientLifecycleAuditRecord record) {
        log.info("AKSK_SERVER_CLIENT_LIFECYCLE_AUDIT eventType={}, clientId={}, ownerSourceId={}, "
                        + "ownerSubjectId={}, targetApplicationId={}, lifecycleVersion={}",
                record.getEventType(), record.getClientId(), record.getOwnerSourceId(), record.getOwnerSubjectId(),
                record.getTargetApplicationId(), record.getLifecycleVersion());
    }
}
