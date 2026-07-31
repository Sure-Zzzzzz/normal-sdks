package io.github.surezzzzzz.sdk.crm.server.core.port.repository;

import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;

import java.util.List;

/**
 * 支持报价确认重放的履约项权威仓储端口。
 *
 * <p>实现必须按租户和订单完整读取已提交履约项；确认报价的重放适配器必须在当前数据范围校验后使用该结果重建
 * QuotationConfirmation，且不得返回其他订单或租户的履约项。</p>
 *
 * @author surezzzzzz
 */
public interface ReplayableFulfillmentItemRepository extends FulfillmentItemRepository {

    /**
     * 按租户和订单读取完整履约事实集合。
     *
     * @param tenantId 租户唯一标识
     * @param orderId  订单唯一标识
     * @return 已提交的履约项集合
     */
    List<FulfillmentItem> findByOrderId(String tenantId, String orderId);
}
