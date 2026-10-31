package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.IntegrationPlanEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** license_integration_plan 仓储（IAM 角色联动计划：MANUAL 清单+命令状态）。 */
public interface IntegrationPlanRepository extends JpaRepository<IntegrationPlanEntity, String> {

    Optional<IntegrationPlanEntity> findByCommandId(String commandId);

    List<IntegrationPlanEntity> findByCustomerIdAndProductIdAndApplicationIdOrderByDesiredRevisionDesc(
            String customerId, String productId, String applicationId);

    List<IntegrationPlanEntity> findByCustomerId(String customerId);

    /** 受限 DATA 范围列表过滤（license-integration 资源 customerId 维度）。 */
    List<IntegrationPlanEntity> findByCustomerIdIn(Iterable<String> customerIds);
}
