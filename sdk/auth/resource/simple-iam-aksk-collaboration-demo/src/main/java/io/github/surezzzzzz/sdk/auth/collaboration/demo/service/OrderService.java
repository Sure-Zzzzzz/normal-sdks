package io.github.surezzzzzz.sdk.auth.collaboration.demo.service;

import io.github.surezzzzzz.sdk.auth.collaboration.demo.dto.OrderCreateRequest;
import io.github.surezzzzzz.sdk.auth.collaboration.demo.dto.OrderResponse;
import io.github.surezzzzzz.sdk.auth.collaboration.demo.entity.OrderEntity;
import io.github.surezzzzzz.sdk.auth.collaboration.demo.repository.OrderRepository;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataAccessPlanRestrictionVerifier;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 订单业务服务，所有查询与写入都执行 DataAccessPlan。
 *
 * <p>越权目标不区分"存在但不可见"与"不存在"，一律 404，避免泄露授权范围外信息。</p>
 *
 * @author surezzzzzz
 */
@Service
public class OrderService {

    private static final String ORDER_CREATED_STATUS = "CREATED";

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * 按计划列订单。
     *
     * @param plan 已验证的数据访问计划
     * @return 授权范围内的订单
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> list(DataAccessPlan plan) {
        requireAllowed(plan);
        List<OrderEntity> entities = orderRepository.findAll(scopeOf(plan));
        return entities.stream().map(OrderResponse::new).collect(Collectors.toList());
    }

    /**
     * 按计划读取单条订单。
     *
     * @param plan 已验证的数据访问计划
     * @param id   订单 id
     * @return 授权范围内的订单
     */
    @Transactional(readOnly = true)
    public OrderResponse detail(DataAccessPlan plan, Long id) {
        return new OrderResponse(findScopedEntity(plan, id));
    }

    /**
     * 在授权目标范围内创建订单。
     *
     * @param plan    已验证的数据访问计划
     * @param request 创建请求
     * @return 创建后的订单
     */
    @Transactional
    public OrderResponse create(DataAccessPlan plan, OrderCreateRequest request) {
        requireAllowed(plan);
        DataAccessPlanRestrictionVerifier.requireTargetAllowed(plan,
                OrderDataAccessPlanConverter.targetDimensions(request.getTenantId(), request.getDepartmentId()));
        OrderEntity entity = new OrderEntity();
        entity.setTenantId(request.getTenantId());
        entity.setDepartmentId(request.getDepartmentId());
        entity.setOrderNo(request.getOrderNo());
        entity.setAmount(request.getAmount());
        entity.setStatus(ORDER_CREATED_STATUS);
        entity.setCreatedAt(LocalDateTime.now());
        return new OrderResponse(orderRepository.save(entity));
    }

    /**
     * 在授权目标范围内删除订单。
     *
     * @param plan 已验证的数据访问计划
     * @param id   订单 id
     */
    @Transactional
    public void delete(DataAccessPlan plan, Long id) {
        orderRepository.delete(findScopedEntity(plan, id));
    }

    private Specification<OrderEntity> scopeOf(DataAccessPlan plan) {
        return OrderDataAccessPlanConverter.toSpecification(plan);
    }

    private OrderEntity findScopedEntity(DataAccessPlan plan, Long id) {
        requireAllowed(plan);
        Specification<OrderEntity> scope = scopeOf(plan);
        if (scope != null) {
            scope = scope.and((root, query, cb) -> cb.equal(root.get("id"), id));
        } else {
            scope = (root, query, cb) -> cb.equal(root.get("id"), id);
        }
        return orderRepository.findOne(scope).orElseThrow(notFound());
    }

    private void requireAllowed(DataAccessPlan plan) {
        if (OrderDataAccessPlanConverter.isDenied(plan)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前身份没有订单数据授权");
        }
    }

    private Supplier<ResponseStatusException> notFound() {
        return () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在或不在授权范围内");
    }
}
