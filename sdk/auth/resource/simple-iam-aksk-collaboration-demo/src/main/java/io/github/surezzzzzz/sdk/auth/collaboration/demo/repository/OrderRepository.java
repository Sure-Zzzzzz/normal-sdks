package io.github.surezzzzzz.sdk.auth.collaboration.demo.repository;

import io.github.surezzzzzz.sdk.auth.collaboration.demo.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 订单仓库，支持按 DataAccessPlan 生成的动态查询条件。
 *
 * @author surezzzzzz
 */
public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {
}
