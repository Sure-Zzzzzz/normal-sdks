package io.github.surezzzzzz.sdk.audit.aksk.server.listener;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerClientLifecycleAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerClientLifecycleAuditRecord;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AkskClientLifecycleEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 在 AKU 生命周期事务成功提交后分发审计记录。
 */
@Slf4j
public class ServerClientLifecycleAuditEventListener {

    private final List<ServerClientLifecycleAuditHandler> auditHandlers;

    public ServerClientLifecycleAuditEventListener(List<ServerClientLifecycleAuditHandler> auditHandlers) {
        this.auditHandlers = auditHandlers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClientLifecycleEvent(AkskClientLifecycleEvent event) {
        ServerClientLifecycleAuditRecord record = ServerClientLifecycleAuditRecord.builder()
                .eventType(event.getEventType())
                .eventTime(event.getEventTime())
                .clientId(event.getClientId())
                .ownerSourceId(event.getOwnerSourceId())
                .ownerSubjectId(event.getOwnerSubjectId())
                .targetApplicationId(event.getTargetApplicationId())
                .lifecycleVersion(event.getLifecycleVersion())
                .build();
        for (ServerClientLifecycleAuditHandler handler : auditHandlers) {
            try {
                handler.handle(record);
            } catch (RuntimeException exception) {
                log.error("AKU生命周期审计处理失败: handler={}, eventType={}, clientId={}",
                        handler.getClass().getName(), event.getEventType(), event.getClientId(), exception);
            }
        }
    }
}
