package io.github.surezzzzzz.sdk.audit.aksk.resource.listener;

import io.github.surezzzzzz.sdk.audit.aksk.resource.annotation.SimpleAkskResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.resource.provider.AkskAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * AKSK 审计事件监听器
 *
 * <p>监听 AkskAccessEvent 事件，转换为 AkskAuditRecord 并调用业务处理器。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleAkskResourceAuditListenerComponent
@ConditionalOnBean(AkskAuditHandler.class)
public class AkskAuditEventListener {

    private final List<AkskAuditHandler> auditHandlers;
    private final AkskAuditTraceIdProvider traceIdProvider;

    public AkskAuditEventListener(
            List<AkskAuditHandler> auditHandlers,
            @Autowired(required = false) AkskAuditTraceIdProvider traceIdProvider) {
        this.auditHandlers = auditHandlers;
        this.traceIdProvider = traceIdProvider;
        log.info("AkskAuditEventListener initialized with {} handlers", auditHandlers.size());
        for (AkskAuditHandler handler : auditHandlers) {
            log.info("  - {}", handler.getClass().getName());
        }
        if (traceIdProvider != null) {
            log.info("AkskAuditTraceIdProvider found: {}", traceIdProvider.getClass().getName());
        } else {
            log.debug("AkskAuditTraceIdProvider not found, traceId will be null in audit records");
        }
    }

    @EventListener
    @Async
    public void onAkskAccessEvent(AkskAccessEvent event) {
        try {
            AkskAuditRecord record = convertToAuditRecord(event);
            for (AkskAuditHandler handler : auditHandlers) {
                try {
                    handler.handle(record);
                } catch (Exception e) {
                    log.error("Handler {} failed to process audit record", handler.getClass().getName(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle AKSK audit event", e);
        }
    }

    private AkskAuditRecord convertToAuditRecord(AkskAccessEvent event) {
        return AkskAuditRecord.builder()
                .clientId(event.getClientId())
                .clientType(event.getClientType())
                .userId(event.getUserId())
                .username(event.getUsername())
                .roles(event.getRoles())
                .scope(event.getScope())
                .requestUri(event.getRequestUri())
                .httpMethod(event.getHttpMethod())
                .remoteAddr(event.getRemoteAddr())
                .userAgent(event.getUserAgent())
                .timestamp(event.getTimestamp())
                .source(event.getSource())
                .traceId(getTraceId())
                .context(event.getContext())
                .build();
    }

    private String getTraceId() {
        if (traceIdProvider == null) {
            return null;
        }
        try {
            return traceIdProvider.getTraceId();
        } catch (Exception e) {
            log.debug("Failed to get traceId from provider", e);
            return null;
        }
    }
}
