package io.github.surezzzzzz.sdk.auth.collaboration.demo.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.collaboration.demo.dto.OrderCreateRequest;
import io.github.surezzzzzz.sdk.auth.collaboration.demo.dto.OrderResponse;
import io.github.surezzzzzz.sdk.auth.collaboration.demo.service.OrderService;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单接口：IAM 人员身份与 AKSK 服务身份进入同一个 Controller、Service 与数据边界。
 *
 * <p>Controller 不读取身份来源、Token payload，也不自行判断授权范围。</p>
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final String ORDER_RESOURCE = "order";
    private static final String READ_ACTION = "read";
    private static final String WRITE_ACTION = "write";
    private static final String ORDER_READ_PERMISSION = "order.read";
    private static final String ORDER_WRITE_PERMISSION = "order.write";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 列出授权范围内的订单。
     *
     * @param plan 当前请求的数据访问计划
     * @return 订单列表
     */
    @GetMapping
    @RequireApiPermission(ORDER_READ_PERMISSION)
    @DataPermissionOperation(resource = ORDER_RESOURCE, action = READ_ACTION)
    public List<OrderResponse> list(@CurrentDataAccessPlan DataAccessPlan plan) {
        return orderService.list(plan);
    }

    /**
     * 读取单条授权范围内的订单。
     *
     * @param plan 当前请求的数据访问计划
     * @param id   订单 id
     * @return 订单详情
     */
    @GetMapping("/{id}")
    @RequireApiPermission(ORDER_READ_PERMISSION)
    @DataPermissionOperation(resource = ORDER_RESOURCE, action = READ_ACTION)
    public OrderResponse detail(@CurrentDataAccessPlan DataAccessPlan plan, @PathVariable Long id) {
        return orderService.detail(plan, id);
    }

    /**
     * 在授权目标范围内创建订单。
     *
     * @param plan    当前请求的数据访问计划
     * @param request 创建请求
     * @return 创建后的订单
     */
    @PostMapping
    @RequireApiPermission(ORDER_WRITE_PERMISSION)
    @DataPermissionOperation(resource = ORDER_RESOURCE, action = WRITE_ACTION)
    public OrderResponse create(@CurrentDataAccessPlan DataAccessPlan plan, @RequestBody OrderCreateRequest request) {
        return orderService.create(plan, request);
    }

    /**
     * 删除授权范围内的订单。
     *
     * @param plan 当前请求的数据访问计划
     * @param id   订单 id
     */
    @DeleteMapping("/{id}")
    @RequireApiPermission(ORDER_WRITE_PERMISSION)
    @DataPermissionOperation(resource = ORDER_RESOURCE, action = WRITE_ACTION)
    public void delete(@CurrentDataAccessPlan DataAccessPlan plan, @PathVariable Long id) {
        orderService.delete(plan, id);
    }
}
