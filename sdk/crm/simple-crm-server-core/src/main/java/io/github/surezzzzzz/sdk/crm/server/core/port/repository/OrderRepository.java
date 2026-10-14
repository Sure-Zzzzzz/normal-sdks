package io.github.surezzzzzz.sdk.crm.server.core.port.repository;

import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;

import java.util.Optional;

/**
 * Order 权威仓储端口。
 *
 * @author surezzzzzz
 */
public interface OrderRepository {

    /**
     * 按租户和标识查询权威事实。
     *
     * @param orderId 订单唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Optional<Order> findById(String orderId);

    /**
     * 按租户和报价版本查询订单。
     *
     * @param quotationId      报价唯一标识
     * @param quotationVersion 报价版本事实
     * @return 处理后的领域事实或校验结果。
     */
    Optional<Order> findByQuotationVersion(String quotationId, int quotationVersion);

    /**
     * 新增权威领域事实。
     *
     * @param order 由报价确认生成的订单事实
     * @return 处理后的领域事实或校验结果。
     */
    Order insert(Order order);
}
