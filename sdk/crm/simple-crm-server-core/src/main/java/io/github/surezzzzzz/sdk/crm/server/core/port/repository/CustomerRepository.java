package io.github.surezzzzzz.sdk.crm.server.core.port.repository;

import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;

import java.util.Optional;

/**
 * Customer 权威仓储端口。
 *
 * @author surezzzzzz
 */
public interface CustomerRepository {

    /**
     * 按租户和标识查询权威事实。
     *
     * @param tenantId   租户唯一标识
     * @param customerId 客户唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Optional<Customer> findById(String tenantId, String customerId);

    /**
     * 新增权威领域事实。
     *
     * @param tenantId 租户唯一标识
     * @param customer 已按租户边界加载的客户事实
     * @return 处理后的领域事实或校验结果。
     */
    Customer insert(String tenantId, Customer customer);

    /**
     * 按预期聚合版本更新权威领域事实。
     *
     * @param tenantId                 租户唯一标识
     * @param customer                 已按租户边界加载的客户事实
     * @param expectedAggregateVersion 调用方预期的聚合版本
     * @return 处理后的领域事实或校验结果。
     */
    Customer update(String tenantId, Customer customer, long expectedAggregateVersion);
}
