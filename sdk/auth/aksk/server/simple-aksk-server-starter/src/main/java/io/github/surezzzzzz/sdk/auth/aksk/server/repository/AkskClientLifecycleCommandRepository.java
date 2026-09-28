package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientLifecycleCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AKU 自助命令幂等账本 Repository。
 */
@Repository
public interface AkskClientLifecycleCommandRepository extends JpaRepository<AkskClientLifecycleCommandEntity, String> {

    Optional<AkskClientLifecycleCommandEntity> findByOwnerSourceIdAndOwnerSubjectIdAndCommandTypeAndIdempotencyKey(
            String ownerSourceId, String ownerSubjectId, String commandType, String idempotencyKey);
}
