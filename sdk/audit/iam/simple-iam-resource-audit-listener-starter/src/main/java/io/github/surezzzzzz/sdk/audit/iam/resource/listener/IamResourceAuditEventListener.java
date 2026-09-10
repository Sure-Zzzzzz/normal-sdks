package io.github.surezzzzzz.sdk.audit.iam.resource.listener;

import io.github.surezzzzzz.sdk.audit.iam.resource.annotation.SimpleIamResourceAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.iam.resource.handler.IamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;
import io.github.surezzzzzz.sdk.audit.iam.resource.provider.IamResourceAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * IAM 资源访问审计事件监听器
 *
 * <p>订阅公共资源层的 {@code ResourceAccessEvent}，只处理 IAM 来源的已认证访问；
 * 其他来源（如 AKSK）的事件由各自的审计挂件处理，本监听器直接忽略。
 * 异步分发到全部 {@code IamResourceAuditHandler}，单个处理器失败只记录告警，
 * 不影响其他处理器，也不影响业务请求。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleIamResourceAuditListenerComponent
@ConditionalOnBean(IamResourceAuditHandler.class)
public class IamResourceAuditEventListener {

    private final List<IamResourceAuditHandler> auditHandlers;
    private final IamResourceAuditTraceIdProvider traceIdProvider;

    public IamResourceAuditEventListener(
            List<IamResourceAuditHandler> auditHandlers,
            @Autowired(required = false) IamResourceAuditTraceIdProvider traceIdProvider) {
        this.auditHandlers = auditHandlers;
        this.traceIdProvider = traceIdProvider;
        log.info("IAM资源访问审计监听器已初始化，共{}个处理器", auditHandlers.size());
        for (IamResourceAuditHandler handler : auditHandlers) {
            log.info("  - {}", handler.getClass().getName());
        }
    }

    /**
     * 处理公共资源访问事件：非 IAM 来源直接忽略；IAM 来源转换为审计记录后异步分发。
     */
    @EventListener
    @Async
    public void onResourceAccessEvent(ResourceAccessEvent event) {
        if (!SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID.equals(event.getAuthenticationSourceId())) {
            return;
        }
        try {
            IamResourceAuditRecord record = convertToAuditRecord(event);
            for (IamResourceAuditHandler handler : auditHandlers) {
                try {
                    handler.handle(record);
                } catch (Exception exception) {
                    log.error("IAM资源审计处理器执行失败: {}", handler.getClass().getName(), exception);
                }
            }
        } catch (Exception exception) {
            log.error("IAM资源审计事件处理失败", exception);
        }
    }

    private IamResourceAuditRecord convertToAuditRecord(ResourceAccessEvent event) {
        return IamResourceAuditRecord.builder()
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
            log.debug("IAM资源审计TraceId获取失败，traceId置空", exception);
            return null;
        }
    }
}
