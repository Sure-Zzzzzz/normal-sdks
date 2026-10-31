package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.RevocationScopeEntity;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 产品吊销导出范围的锁定仓储。 */
public interface RevocationScopeRepository extends JpaRepository<RevocationScopeEntity, String> {

    Optional<RevocationScopeEntity> findByIssuerIdAndProductId(String issuerId, String productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RevocationScopeEntity s where s.issuerId = ?1 and s.productId = ?2")
    Optional<RevocationScopeEntity> lockByIssuerIdAndProductId(String issuerId, String productId);
}
