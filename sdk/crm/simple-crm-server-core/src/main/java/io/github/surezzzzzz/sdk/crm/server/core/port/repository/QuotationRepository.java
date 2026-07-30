package io.github.surezzzzzz.sdk.crm.server.core.port.repository;

import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion;

import java.util.Optional;

/**
 * Quotation 权威仓储端口。
 *
 * @author surezzzzzz
 */
public interface QuotationRepository {

    /**
     * 按租户和标识查询权威事实。
     *
     * @param tenantId    租户唯一标识
     * @param quotationId 报价唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Optional<Quotation> findById(String tenantId, String quotationId);

    /**
     * 按租户、报价和版本号查询报价版本。
     *
     * @param tenantId    租户唯一标识
     * @param quotationId 报价唯一标识
     * @param version     报价版本事实或版本号
     * @return 处理后的领域事实或校验结果。
     */
    Optional<QuotationVersion> findVersion(String tenantId, String quotationId, int version);

    /**
     * 新增权威领域事实。
     *
     * @param tenantId  租户唯一标识
     * @param quotation 报价聚合当前事实
     * @return 处理后的领域事实或校验结果。
     */
    Quotation insert(String tenantId, Quotation quotation);

    /**
     * 新增报价版本事实。
     *
     * @param tenantId         租户唯一标识
     * @param quotationVersion 报价版本事实
     * @return 处理后的领域事实或校验结果。
     */
    QuotationVersion insertVersion(String tenantId, QuotationVersion quotationVersion);

    /**
     * 按预期聚合版本更新权威领域事实。
     *
     * @param tenantId                 租户唯一标识
     * @param quotation                报价聚合当前事实
     * @param expectedAggregateVersion 调用方预期的聚合版本
     * @return 处理后的领域事实或校验结果。
     */
    Quotation update(String tenantId, Quotation quotation, long expectedAggregateVersion);

    /**
     * 按预期状态迁移报价版本。
     *
     * @param tenantId         租户唯一标识
     * @param quotationVersion 迁移后的报价版本事实
     * @param expectedState    迁移前预期状态
     * @return 已持久化的报价版本事实
     */
    QuotationVersion transitionVersionState(String tenantId, QuotationVersion quotationVersion,
                                            QuotationState expectedState);
}
