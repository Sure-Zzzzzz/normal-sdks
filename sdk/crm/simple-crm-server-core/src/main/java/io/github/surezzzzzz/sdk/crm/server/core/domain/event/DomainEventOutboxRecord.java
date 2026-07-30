package io.github.surezzzzzz.sdk.crm.server.core.domain.event;

import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * 已提交但尚未由内部事件适配器投递的领域事件事实。
 *
 * @author surezzzzzz
 */
@Getter
public final class DomainEventOutboxRecord {

    private final String eventId;
    private final String tenantId;
    private final CrmResourceType aggregateType;
    private final String aggregateId;
    private final long eventSequence;
    private final CrmDomainEventType eventType;
    private final String payloadSnapshot;
    private final String payloadHash;
    private final DomainEventState state;
    private final Instant occurredAt;
    private final String correlationId;

    /**
     * 创建DomainEventOutboxRecord。
     *
     * @param eventId         eventId参数。
     * @param tenantId        租户唯一标识
     * @param aggregateType   聚合资源类型
     * @param aggregateId     聚合资源标识
     * @param eventSequence   eventSequence参数。
     * @param eventType       领域事件类型
     * @param payloadSnapshot payloadSnapshot参数。
     * @param payloadHash     请求或消息载荷哈希
     * @param state           业务状态
     * @param occurredAt      审计事实发生时间
     * @param correlationId   命令关联标识
     *
     */
    public DomainEventOutboxRecord(String eventId, String tenantId, CrmResourceType aggregateType,
                                   String aggregateId, long eventSequence, CrmDomainEventType eventType,
                                   String payloadSnapshot, String payloadHash, DomainEventState state,
                                   Instant occurredAt, String correlationId) {
        this.eventId = CrmValidationHelper.required(eventId, "eventId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.aggregateType = CrmValidationHelper.requiredObject(aggregateType, "aggregateType");
        this.aggregateId = CrmValidationHelper.required(aggregateId, "aggregateId");
        this.eventSequence = CrmValidationHelper.positiveVersion(eventSequence, "eventSequence");
        this.eventType = CrmValidationHelper.requiredObject(eventType, "eventType");
        this.payloadSnapshot = CrmValidationHelper.required(payloadSnapshot, "payloadSnapshot");
        this.payloadHash = CrmValidationHelper.sha256(payloadHash, "payloadHash");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.occurredAt = CrmValidationHelper.requiredObject(occurredAt, "occurredAt");
        this.correlationId = CrmValidationHelper.required(correlationId, "correlationId");
    }


}
