package io.github.surezzzzzz.sdk.audit.aksk.resource.listener;

import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.resource.provider.AkskAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * 官方 AKSK 资源审计监听器，只消费公共事件的 AKSK 来源，由自动配置 @Bean 注册。
 *
 * @author surezzzzzz
 */
@Slf4j
public class AkskAuditEventListener {
    private final List<AkskAuditHandler> auditHandlers;
    private final AkskAuditTraceIdProvider traceIdProvider;

    /**
     * 创建监听器，保留旧线的构造签名。
     *
     * @param auditHandlers   处理器列表
     * @param traceIdProvider 可选追踪标识提供者，可为 null
     */
    public AkskAuditEventListener(List<AkskAuditHandler> auditHandlers, AkskAuditTraceIdProvider traceIdProvider) {
        this.auditHandlers = auditHandlers;
        this.traceIdProvider = traceIdProvider;
        log.debug("AKSK资源审计监听器已装配: handlers={}, traceProvider={}",
                auditHandlers.size(), traceIdProvider != null);
    }

    /**
     * 异步转换并分发访问事件；其他认证来源不转换，单个 Handler 失败不影响后续处理器。
     *
     * @param event 公共资源层已认证访问事件
     */
    @EventListener
    @Async
    public void onResourceAccessEvent(ResourceAccessEvent event) {
        if (event == null || !AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID
                .equals(event.getAuthenticationSourceId())) {
            log.debug("AKSK资源审计忽略非AKSK来源或空事件");
            return;
        }
        log.debug("AKSK资源审计开始分发: requestId={}, handlers={}", event.getRequestId(), auditHandlers.size());
        try {
            AkskAuditRecord record = convertToAuditRecord(event);
            for (AkskAuditHandler handler : auditHandlers) {
                try {
                    // 记录字段均为不可变值，每个 Handler 获得独立副本，避免相互修改污染。
                    handler.handle(record.toBuilder().build());
                } catch (Exception exception) {
                    log.error("AKSK审计处理器执行失败: handler={}, exceptionType={}",
                            handler.getClass().getName(), exception.getClass().getName());
                }
            }
            log.debug("AKSK资源审计分发结束: requestId={}", event.getRequestId());
        } catch (Exception exception) {
            log.error("AKSK资源审计事件转换失败: exceptionType={}", exception.getClass().getName());
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
            log.debug("AKSK审计追踪标识获取失败，保留空值: exceptionType={}", exception.getClass().getName());
            return null;
        }
    }
}
