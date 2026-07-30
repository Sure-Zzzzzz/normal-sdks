package io.github.surezzzzzz.sdk.crm.server.core.port.repository;

import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;

import java.util.Optional;

/**
 * Contact 权威仓储端口。
 *
 * @author surezzzzzz
 */
public interface ContactRepository {

    /**
     * 按租户和标识查询权威事实。
     *
     * @param tenantId  租户唯一标识
     * @param contactId 联系人唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Optional<Contact> findById(String tenantId, String contactId);

    /**
     * 新增权威领域事实。
     *
     * @param tenantId 租户唯一标识
     * @param contact  contact参数。
     * @return 处理后的领域事实或校验结果。
     */
    Contact insert(String tenantId, Contact contact);

    /**
     * 按预期聚合版本更新权威领域事实。
     *
     * @param tenantId                 租户唯一标识
     * @param contact                  contact参数。
     * @param expectedAggregateVersion 调用方预期的聚合版本
     * @return 处理后的领域事实或校验结果。
     */
    Contact update(String tenantId, Contact contact, long expectedAggregateVersion);
}
