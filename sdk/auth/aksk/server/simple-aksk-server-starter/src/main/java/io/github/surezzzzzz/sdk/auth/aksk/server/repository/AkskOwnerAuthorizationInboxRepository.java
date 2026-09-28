package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationInboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AKSK 本地 owner 授权 Inbox 仓储。
 */
@Repository
public interface AkskOwnerAuthorizationInboxRepository
        extends JpaRepository<AkskOwnerAuthorizationInboxEntity, String> {
}
