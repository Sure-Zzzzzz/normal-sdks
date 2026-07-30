package io.github.surezzzzzz.sdk.crm.server.core.domain.customer;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * CRM 交易关系主体。
 *
 * @author surezzzzzz
 */
@Getter
public final class Customer {

    private final String customerId;
    private final String tenantId;
    private final String displayName;
    private final CustomerState state;
    private final String ownerActorId;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建Customer。
     *
     * @param customerId       客户唯一标识
     * @param tenantId         租户唯一标识
     * @param displayName      展示名称
     * @param state            业务状态
     * @param ownerActorId     报价归属操作者标识
     * @param aggregateVersion 聚合版本
     * @param createdAt        创建时间
     * @param updatedAt        变更后的更新时间
     *
     */
    public Customer(String customerId, String tenantId, String displayName, CustomerState state,
                    String ownerActorId, long aggregateVersion, Instant createdAt, Instant updatedAt) {
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.displayName = CrmValidationHelper.required(displayName, "displayName");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.ownerActorId = CrmValidationHelper.required(ownerActorId, "ownerActorId");
        this.aggregateVersion = CrmValidationHelper.positiveVersion(aggregateVersion, "aggregateVersion");
        this.createdAt = CrmValidationHelper.requiredObject(createdAt, "createdAt");
        this.updatedAt = CrmValidationHelper.requiredObject(updatedAt, "updatedAt");
    }


}
