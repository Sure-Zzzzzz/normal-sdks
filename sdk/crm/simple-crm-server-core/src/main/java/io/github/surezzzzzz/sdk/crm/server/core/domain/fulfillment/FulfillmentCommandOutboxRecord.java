package io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * 已冻结、待由 HTTP 履约适配器投递的命令事实。
 *
 * @author surezzzzzz
 */
@Getter
public final class FulfillmentCommandOutboxRecord {

    private final String outboxId;
    private final String tenantId;
    private final String fulfillmentId;
    private final int fulfillmentVersion;
    private final String consumerId;
    private final int protocolVersion;
    private final String payloadSnapshot;
    private final String payloadHash;
    private final FulfillmentCommandState state;
    private final Instant createdAt;
    private final String correlationId;

    /**
     * 创建FulfillmentCommandOutboxRecord。
     *
     * @param outboxId           Outbox 记录唯一标识
     * @param tenantId           租户唯一标识
     * @param fulfillmentId      履约项唯一标识
     * @param fulfillmentVersion fulfillmentVersion参数。
     * @param consumerId         consumerId参数。
     * @param protocolVersion    消费者协议版本
     * @param payloadSnapshot    payloadSnapshot参数。
     * @param payloadHash        请求或消息载荷哈希
     * @param state              业务状态
     * @param createdAt          创建时间
     * @param correlationId      命令关联标识
     *
     */
    public FulfillmentCommandOutboxRecord(String outboxId, String tenantId, String fulfillmentId,
                                          int fulfillmentVersion, String consumerId, int protocolVersion,
                                          String payloadSnapshot, String payloadHash,
                                          FulfillmentCommandState state, Instant createdAt,
                                          String correlationId) {
        this.outboxId = CrmValidationHelper.required(outboxId, "outboxId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.fulfillmentId = CrmValidationHelper.required(fulfillmentId, "fulfillmentId");
        this.fulfillmentVersion = CrmValidationHelper.positiveVersion(fulfillmentVersion,
                "fulfillmentVersion");
        this.consumerId = CrmValidationHelper.required(consumerId, "consumerId");
        this.protocolVersion = CrmValidationHelper.positiveVersion(protocolVersion, "protocolVersion");
        this.payloadSnapshot = CrmValidationHelper.required(payloadSnapshot, "payloadSnapshot");
        this.payloadHash = CrmValidationHelper.sha256(payloadHash, "payloadHash");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.createdAt = CrmValidationHelper.requiredObject(createdAt, "createdAt");
        this.correlationId = CrmValidationHelper.required(correlationId, "correlationId");
    }


}
