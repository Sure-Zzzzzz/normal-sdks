package io.github.surezzzzzz.sdk.crm.server.entity;

import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.ContactState;
import lombok.Getter;

import java.time.Instant;

/**
 * 联系人 JDBC 映射实体。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerContactEntity {

    private final String contactId;
    private final String customerId;
    private final String displayName;
    private final String title;
    private final String stateCode;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建SimpleCrmServerContactEntity。
     *
     * @param contactId        联系人唯一标识
     * @param customerId       归属客户唯一标识
     * @param displayName      展示名称
     * @param title            职务（可空）
     * @param stateCode        状态稳定 code
     * @param aggregateVersion 聚合版本
     * @param createdAt        创建时间
     * @param updatedAt        变更时间
     */
    public SimpleCrmServerContactEntity(String contactId, String customerId, String displayName, String title,
                         String stateCode, long aggregateVersion, Instant createdAt, Instant updatedAt) {
        this.contactId = contactId;
        this.customerId = customerId;
        this.displayName = displayName;
        this.title = title;
        this.stateCode = stateCode;
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 实体转领域事实。
     *
     * @return 联系人领域事实
     */
    public Contact toDomain() {
        return new Contact(contactId, customerId, displayName, title, ContactState.fromCode(stateCode),
                aggregateVersion, createdAt, updatedAt);
    }

    /**
     * 领域事实转实体。
     *
     * @param contact 联系人领域事实
     * @return JDBC 映射实体
     */
    public static SimpleCrmServerContactEntity from(Contact contact) {
        return new SimpleCrmServerContactEntity(contact.getContactId(), contact.getCustomerId(), contact.getDisplayName(),
                contact.getTitle(), contact.getState().getCode(), contact.getAggregateVersion(),
                contact.getCreatedAt(), contact.getUpdatedAt());
    }
}
