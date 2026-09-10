package io.github.surezzzzzz.sdk.auth.collaboration.demo.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 订单创建请求，tenantId 与 departmentId 是 DATA 授权目标维度。
 *
 * @author surezzzzzz
 */
@Getter
@Setter
public class OrderCreateRequest {

    private String tenantId;
    private String departmentId;
    private String orderNo;
    private BigDecimal amount;
}
