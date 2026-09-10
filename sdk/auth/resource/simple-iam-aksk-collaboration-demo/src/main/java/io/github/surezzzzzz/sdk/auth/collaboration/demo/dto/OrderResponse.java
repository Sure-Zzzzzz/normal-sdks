package io.github.surezzzzzz.sdk.auth.collaboration.demo.dto;

import io.github.surezzzzzz.sdk.auth.collaboration.demo.entity.OrderEntity;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单响应。
 *
 * @author surezzzzzz
 */
@Getter
public class OrderResponse {

    private final Long id;
    private final String tenantId;
    private final String departmentId;
    private final String orderNo;
    private final BigDecimal amount;
    private final String status;
    private final LocalDateTime createdAt;

    public OrderResponse(OrderEntity entity) {
        this.id = entity.getId();
        this.tenantId = entity.getTenantId();
        this.departmentId = entity.getDepartmentId();
        this.orderNo = entity.getOrderNo();
        this.amount = entity.getAmount();
        this.status = entity.getStatus();
        this.createdAt = entity.getCreatedAt();
    }
}
