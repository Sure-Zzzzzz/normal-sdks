package io.github.surezzzzzz.sdk.crm.server.entity;

import lombok.Getter;

import java.time.Instant;

/**
 * 履约项 JDBC 映射实体。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerFulfillmentItemEntity {

    private final String fulfillmentId;
    private final String orderId;
    private final String orderLineId;
    private final String owner;
    private final String assignedConsumerId;
    private final int consumerProtocolVersion;
    private final String stateCode;
    private final int reportSequence;
    private final String obligationTemplateJson;
    private final String resultData;
    private final String resultNote;
    private final String reportedBy;
    private final Instant reportedAt;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建SimpleCrmServerFulfillmentItemEntity。
     *
     * @param fulfillmentId           履约项唯一标识
     * @param orderId                 订单唯一标识
     * @param orderLineId             订单行唯一标识
     * @param owner                   归属主体（继承报价 owner，人员查询维度）
     * @param assignedConsumerId      冻结消费者标识（机器回写授权维度）
     * @param consumerProtocolVersion 消费者协议版本
     * @param stateCode               状态稳定 code
     * @param reportSequence          已回写序号水位
     * @param obligationTemplateJson  履约义务模板快照 JSON
     * @param resultData              最近回写结果数据（可空）
     * @param resultNote              最近回写备注（可空）
     * @param reportedBy              最近回写人 owner（可空）
     * @param reportedAt              最近回写时间（可空）
     * @param aggregateVersion        聚合版本
     * @param createdAt               创建时间
     * @param updatedAt               变更时间
     */
    public SimpleCrmServerFulfillmentItemEntity(String fulfillmentId, String orderId, String orderLineId, String owner,
                                 String assignedConsumerId, int consumerProtocolVersion, String stateCode,
                                 int reportSequence, String obligationTemplateJson, String resultData,
                                 String resultNote, String reportedBy, Instant reportedAt, long aggregateVersion,
                                 Instant createdAt, Instant updatedAt) {
        this.fulfillmentId = fulfillmentId;
        this.orderId = orderId;
        this.orderLineId = orderLineId;
        this.owner = owner;
        this.assignedConsumerId = assignedConsumerId;
        this.consumerProtocolVersion = consumerProtocolVersion;
        this.stateCode = stateCode;
        this.reportSequence = reportSequence;
        this.obligationTemplateJson = obligationTemplateJson;
        this.resultData = resultData;
        this.resultNote = resultNote;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
