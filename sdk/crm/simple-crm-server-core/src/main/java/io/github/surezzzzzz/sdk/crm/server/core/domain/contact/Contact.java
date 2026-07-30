package io.github.surezzzzzz.sdk.crm.server.core.domain.contact;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * 归属 Customer 的联系人。
 *
 * @author surezzzzzz
 */
@Getter
public final class Contact {

    private final String contactId;
    private final String tenantId;
    private final String customerId;
    private final String displayName;
    private final String title;
    private final ContactState state;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建Contact。
     *
     * @param contactId        联系人唯一标识
     * @param tenantId         租户唯一标识
     * @param customerId       客户唯一标识
     * @param displayName      展示名称
     * @param title            职务名称
     * @param state            业务状态
     * @param aggregateVersion 聚合版本
     * @param createdAt        创建时间
     * @param updatedAt        变更后的更新时间
     *
     */
    public Contact(String contactId, String tenantId, String customerId, String displayName, String title,
                   ContactState state, long aggregateVersion, Instant createdAt, Instant updatedAt) {
        this.contactId = CrmValidationHelper.required(contactId, "contactId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.displayName = CrmValidationHelper.required(displayName, "displayName");
        this.title = CrmValidationHelper.optional(title);
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.aggregateVersion = CrmValidationHelper.positiveVersion(aggregateVersion, "aggregateVersion");
        this.createdAt = CrmValidationHelper.requiredObject(createdAt, "createdAt");
        this.updatedAt = CrmValidationHelper.requiredObject(updatedAt, "updatedAt");
    }


}
