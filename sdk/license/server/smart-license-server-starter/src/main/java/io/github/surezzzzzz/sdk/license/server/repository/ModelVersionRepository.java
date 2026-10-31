package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** license_model_version 仓储。 */
public interface ModelVersionRepository extends JpaRepository<ModelVersionEntity, String> {

    List<ModelVersionEntity> findByProductIdOrderByVersionDesc(String productId);

    List<ModelVersionEntity> findByModelIdOrderByVersionDesc(String modelId);

    Optional<ModelVersionEntity> findByModelIdAndVersion(String modelId, long version);

    /** 受限 DATA 范围列表过滤（按产品维度 IN 集合）。 */
    List<ModelVersionEntity> findByProductIdIn(Iterable<String> productIds);
}
