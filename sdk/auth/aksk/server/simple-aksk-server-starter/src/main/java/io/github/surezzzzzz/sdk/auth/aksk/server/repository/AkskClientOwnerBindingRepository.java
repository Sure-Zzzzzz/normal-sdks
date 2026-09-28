package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientOwnerBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AKSK 所属人绑定 Repository。
 */
@Repository
public interface AkskClientOwnerBindingRepository extends JpaRepository<AkskClientOwnerBindingEntity, String> {

    Optional<AkskClientOwnerBindingEntity> findByClientId(String clientId);

    List<AkskClientOwnerBindingEntity> findByOwnerSourceIdAndOwnerSubjectIdOrderByBoundAtDesc(
            String ownerSourceId, String ownerSubjectId);
}
