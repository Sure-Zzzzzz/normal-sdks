package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.PlanVersionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** license_plan_version 仓储。 */
public interface PlanVersionRepository extends JpaRepository<PlanVersionEntity, String> {

    List<PlanVersionEntity> findByProductId(String productId);

    /** 受限 DATA 范围列表过滤（按产品维度 IN 集合）。 */
    List<PlanVersionEntity> findByProductIdIn(Iterable<String> productIds);
}
