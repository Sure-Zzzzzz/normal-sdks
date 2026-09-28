package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AKSK 人员-目标应用授权投影仓储。
 */
@Repository
public interface AkskOwnerAuthorizationProjectionRepository
        extends JpaRepository<AkskOwnerAuthorizationProjectionEntity, String> {
}
