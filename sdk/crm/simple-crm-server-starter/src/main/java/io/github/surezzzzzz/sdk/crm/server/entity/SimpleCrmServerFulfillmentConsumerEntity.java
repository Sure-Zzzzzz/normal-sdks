package io.github.surezzzzzz.sdk.crm.server.entity;

import lombok.Getter;

import java.time.Instant;

/**
 * 履约消费者注册表 JDBC 映射实体（无端点无凭据）。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerFulfillmentConsumerEntity {

    private final String consumerId;
    private final String capability;
    private final int protocolVersion;
    private final boolean enabled;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建SimpleCrmServerFulfillmentConsumerEntity。
     *
     * @param consumerId       消费者标识（须绑定其 AKP clientId）
     * @param capability       满足的消费者能力
     * @param protocolVersion  协议版本
     * @param enabled          是否启用
     * @param aggregateVersion 聚合版本
     * @param createdAt        创建时间
     * @param updatedAt        变更时间
     */
    public SimpleCrmServerFulfillmentConsumerEntity(String consumerId, String capability, int protocolVersion, boolean enabled,
                                     long aggregateVersion, Instant createdAt, Instant updatedAt) {
        this.consumerId = consumerId;
        this.capability = capability;
        this.protocolVersion = protocolVersion;
        this.enabled = enabled;
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
