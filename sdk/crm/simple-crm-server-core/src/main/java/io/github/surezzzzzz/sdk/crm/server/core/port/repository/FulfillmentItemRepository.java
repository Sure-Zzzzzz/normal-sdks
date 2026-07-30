package io.github.surezzzzzz.sdk.crm.server.core.port.repository;

import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;

/**
 * FulfillmentItem 权威仓储端口。
 *
 * @author surezzzzzz
 */
public interface FulfillmentItemRepository {

    /**
     * 新增权威领域事实。
     *
     * @param tenantId        租户唯一标识
     * @param fulfillmentItem fulfillmentItem参数。
     * @return 处理后的领域事实或校验结果。
     */
    FulfillmentItem insert(String tenantId, FulfillmentItem fulfillmentItem);
}
