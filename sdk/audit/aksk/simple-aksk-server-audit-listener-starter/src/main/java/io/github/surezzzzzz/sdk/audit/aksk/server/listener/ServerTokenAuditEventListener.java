package io.github.surezzzzzz.sdk.audit.aksk.server.listener;

import io.github.surezzzzzz.sdk.audit.aksk.server.annotation.SimpleAkskServerAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AbstractTokenEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenIntrospectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * Server Token 审计事件监听器
 *
 * <p>监听 AbstractTokenEvent 及其子类事件，转换为 ServerTokenAuditRecord 并调用所有处理器。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleAkskServerAuditListenerComponent
@ConditionalOnBean(ServerTokenAuditHandler.class)
public class ServerTokenAuditEventListener {

    private final List<ServerTokenAuditHandler> auditHandlers;

    public ServerTokenAuditEventListener(List<ServerTokenAuditHandler> auditHandlers) {
        this.auditHandlers = auditHandlers;
        log.info("ServerTokenAuditEventListener initialized with {} handlers", auditHandlers.size());
        for (ServerTokenAuditHandler handler : auditHandlers) {
            log.info("  - {}", handler.getClass().getName());
        }
    }

    @EventListener
    @Async
    public void onTokenEvent(AbstractTokenEvent event) {
        try {
            ServerTokenAuditRecord record = convertToAuditRecord(event);
            for (ServerTokenAuditHandler handler : auditHandlers) {
                try {
                    handler.handle(record);
                } catch (Exception e) {
                    log.error("Handler {} failed to process server token audit record", handler.getClass().getName(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle server token audit event", e);
        }
    }

    private ServerTokenAuditRecord convertToAuditRecord(AbstractTokenEvent event) {
        ServerTokenAuditRecord.ServerTokenAuditRecordBuilder builder = ServerTokenAuditRecord.builder()
                .eventType(event.getEventType())
                .eventTime(event.getEventTime())
                .clientId(event.getClientId())
                .clientType(event.getClientType())
                .userId(event.getUserId())
                .username(event.getUsername())
                .scopes(event.getScopes())
                .tokenValue(event.getTokenValue())
                .issuedAt(event.getIssuedAt())
                .expiresAt(event.getExpiresAt());

        if (event instanceof TokenIntrospectedEvent) {
            builder.active(((TokenIntrospectedEvent) event).isActive());
        }

        return builder.build();
    }
}
