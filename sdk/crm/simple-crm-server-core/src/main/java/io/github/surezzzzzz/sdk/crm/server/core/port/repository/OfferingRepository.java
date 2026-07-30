package io.github.surezzzzzz.sdk.crm.server.core.port.repository;

import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;

import java.util.Optional;

/**
 * Offering 权威仓储端口。
 *
 * @author surezzzzzz
 */
public interface OfferingRepository {

    /**
     * 按租户和标识查询权威事实。
     *
     * @param tenantId   租户唯一标识
     * @param offeringId 商品或服务唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Optional<Offering> findById(String tenantId, String offeringId);

    /**
     * 按租户和业务引用查询商品或服务。
     *
     * @param tenantId          租户唯一标识
     * @param offeringReference 商品或服务业务引用
     * @return 处理后的领域事实或校验结果。
     */
    Optional<Offering> findByReference(String tenantId, String offeringReference);

    /**
     * 新增权威领域事实。
     *
     * @param tenantId 租户唯一标识
     * @param offering offering参数。
     * @return 处理后的领域事实或校验结果。
     */
    Offering insert(String tenantId, Offering offering);

    /**
     * 按预期聚合版本更新权威领域事实。
     *
     * @param tenantId                 租户唯一标识
     * @param offering                 offering参数。
     * @param expectedAggregateVersion 调用方预期的聚合版本
     * @return 处理后的领域事实或校验结果。
     */
    Offering update(String tenantId, Offering offering, long expectedAggregateVersion);
}
