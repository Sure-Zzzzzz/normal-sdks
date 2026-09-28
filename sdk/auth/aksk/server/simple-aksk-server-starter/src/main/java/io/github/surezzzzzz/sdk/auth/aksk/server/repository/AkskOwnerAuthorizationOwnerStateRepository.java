package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationOwnerStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AKSK 所属人授权状态仓储。
 */
@Repository
public interface AkskOwnerAuthorizationOwnerStateRepository
        extends JpaRepository<AkskOwnerAuthorizationOwnerStateEntity, String> {
}
