package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.ActivationQuotaAllocationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 部署激活本地额度分配事实仓储。 */
public interface ActivationQuotaAllocationRepository
        extends JpaRepository<ActivationQuotaAllocationEntity, String> {

    List<ActivationQuotaAllocationEntity> findByActivationIdOrderByMetricAsc(String activationId);

    List<ActivationQuotaAllocationEntity> findByGrantId(String grantId);
}
