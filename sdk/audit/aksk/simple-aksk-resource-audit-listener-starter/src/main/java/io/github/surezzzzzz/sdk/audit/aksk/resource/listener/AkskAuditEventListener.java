package io.github.surezzzzzz.sdk.audit.aksk.resource.listener;

import io.github.surezzzzzz.sdk.audit.aksk.resource.annotation.SimpleAkskResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.resource.provider.AkskAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * AKSK资源访问审计事件监听器。
 *
 * @author surezzzzzz
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
    }

    @EventListener
    @Async
    public void onResourceAccessEvent(ResourceAccessEvent event) {
        if (!AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID.equals(event.getAuthenticationSourceId())) {
            return;
        }
        try {
            AkskAuditRecord record = convertToAuditRecord(event);
            for (AkskAuditHandler handler : auditHandlers) {
                try {
                    handler.handle(record);
                } catch (Exception exception) {
                    log.error("AKSK审计处理器执行失败: {}", handler.getClass().getName(), exception);
                }
            }
        } catch (Exception exception) {
            log.error("AKSK资源审计事件处理失败", exception);
        }
    }

    private AkskAuditRecord convertToAuditRecord(ResourceAccessEvent event) {
        return AkskAuditRecord.builder()
                .authenticationSourceId(event.getAuthenticationSourceId())
                .subjectType(event.getSubjectType().name())
                .subjectId(event.getSubjectId())
                .applicationCode(event.getApplicationCode())
                .requestId(event.getRequestId())
                .requestUri(event.getRequestUri())
                .httpMethod(event.getHttpMethod())
                .remoteAddr(event.getRemoteAddr())
                .userAgent(event.getUserAgent())
                .timestamp(event.getTimestamp())
                .traceId(getTraceId())
                .build();
    }

    private String getTraceId() {
        if (traceIdProvider == null) {
            return null;
        }
        try {
            return traceIdProvider.getTraceId();
        } catch (Exception exception) {
            log.debug("获取审计链路标识失败", exception);
            return null;
        }
    }
}
