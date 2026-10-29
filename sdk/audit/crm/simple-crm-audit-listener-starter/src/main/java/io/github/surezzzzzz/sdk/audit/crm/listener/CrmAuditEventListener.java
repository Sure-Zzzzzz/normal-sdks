package io.github.surezzzzzz.sdk.audit.crm.listener;

import io.github.surezzzzzz.sdk.audit.crm.model.CrmAuditRecord;
import io.github.surezzzzzz.sdk.audit.crm.provider.CrmAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.crm.server.core.domain.event.CrmAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

/**
 * CRM 业务审计事件监听器
 *
 * <p>在事件线程先采集链路追踪标识，再交给异步分发器；任何处理器异常都不影响其余
 * 处理器和 CRM 主链路。事件由服务端在事务提交后发布，天然排除未提交操作。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
public class CrmAuditEventListener {

    private final CrmAuditEventDispatcher dispatcher;
    private final CrmAuditTraceIdProvider traceIdProvider;

    /**
     * 创建 CRM 审计监听器
     *
     * @param dispatcher      异步审计处理器分发器
     * @param traceIdProvider 链路追踪 ID 提供者，可缺省
     */
    public CrmAuditEventListener(CrmAuditEventDispatcher dispatcher,
                                 CrmAuditTraceIdProvider traceIdProvider) {
        this.dispatcher = dispatcher;
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * 审计业务命令审计事件
     *
     * @param event 业务命令审计事件
     */
    @EventListener
    public void onBusinessCommandAudited(CrmAuditEvent event) {
        dispatcher.dispatch(CrmAuditRecord.builder()
                .eventType(event.getActionCode())
                .eventId(event.getEventId())
                .eventVersion(Integer.valueOf(event.getEventVersion()))
                .timestamp(Long.valueOf(event.getOccurredAt().toEpochMilli()))
                .owner(event.getOwner())
                .resourceTypeCode(event.getResourceTypeCode())
                .resourceId(event.getResourceId())
                .aggregateVersion(Long.valueOf(event.getAggregateVersion()))
                .correlationId(event.getCorrelationId())
                .resultCode(event.getResultCode())
                .traceId(getTraceId())
                .build());
    }

    private String getTraceId() {
        if (traceIdProvider == null) {
            return null;
        }
        try {
            return traceIdProvider.getTraceId();
        } catch (Exception e) {
            // 追踪扩展属于旁路能力，失败时保留审计记录且不泄露异常内容。
            log.warn("CRM审计链路追踪ID获取失败，降级为空");
            return null;
        }
    }
}
