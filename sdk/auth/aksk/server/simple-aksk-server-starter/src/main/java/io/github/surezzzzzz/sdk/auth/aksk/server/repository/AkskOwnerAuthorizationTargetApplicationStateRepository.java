package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationTargetApplicationStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AKSK 目标可信应用授权状态仓储。
 */
@Repository
public interface AkskOwnerAuthorizationTargetApplicationStateRepository
        extends JpaRepository<AkskOwnerAuthorizationTargetApplicationStateEntity, Long> {
}
