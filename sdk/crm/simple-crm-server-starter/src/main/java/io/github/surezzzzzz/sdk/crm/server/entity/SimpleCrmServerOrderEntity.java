package io.github.surezzzzzz.sdk.crm.server.entity;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderDisplayState;
import lombok.Getter;

import java.time.Instant;

/**
 * 订单 JDBC 映射实体（行项在 crm_order_line 表，由仓储装配）。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerOrderEntity {

    private final String orderId;
    private final String sourceQuotationId;
    private final int sourceQuotationVersion;
    private final String customerId;
    private final String settlementCurrency;
    private final java.math.BigDecimal totalAmount;
    private final String owner;
    private final Instant confirmedAt;
    private final long orderVersion;
    private final String displayStateCode;
    private final Instant updatedAt;

    /**
     * 创建SimpleCrmServerOrderEntity。
     *
     * @param orderId                 订单唯一标识
     * @param sourceQuotationId       来源报价唯一标识
     * @param sourceQuotationVersion  来源报价版本
     * @param customerId              客户唯一标识
     * @param settlementCurrency      结算币种
     * @param totalAmount             总价
     * @param owner                   归属主体（继承报价 owner）
     * @param confirmedAt             确认时间
     * @param orderVersion            聚合版本
     * @param displayStateCode        展示状态稳定 code
     * @param updatedAt               变更时间
     */
    public SimpleCrmServerOrderEntity(String orderId, String sourceQuotationId, int sourceQuotationVersion, String customerId,
                       String settlementCurrency, java.math.BigDecimal totalAmount, String owner,
                       Instant confirmedAt, long orderVersion, String displayStateCode, Instant updatedAt) {
        this.orderId = orderId;
        this.sourceQuotationId = sourceQuotationId;
        this.sourceQuotationVersion = sourceQuotationVersion;
        this.customerId = customerId;
        this.settlementCurrency = settlementCurrency;
        this.totalAmount = totalAmount;
        this.owner = owner;
        this.confirmedAt = confirmedAt;
        this.orderVersion = orderVersion;
        this.displayStateCode = displayStateCode;
        this.updatedAt = updatedAt;
    }

    /**
     * 展示状态枚举。
     *
     * @return 展示状态
     */
    public OrderDisplayState displayState() {
        return OrderDisplayState.fromCode(displayStateCode);
    }
}
