package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationCursorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * AKSK 本地 owner 授权日志游标仓储。
 */
@Repository
public interface AkskOwnerAuthorizationCursorRepository
        extends JpaRepository<AkskOwnerAuthorizationCursorEntity, String> {

    /**
     * 以条件更新原子领取 worker 租约，避免多实例竞争时依赖 JPA 乐观锁异常。
     */
    @Modifying
    @Query("UPDATE AkskOwnerAuthorizationCursorEntity cursor "
            + "SET cursor.workerLeaseOwner = :owner, cursor.workerLeaseUntil = :leaseUntil, "
            + "cursor.updatedAt = :now, cursor.version = cursor.version + 1 "
            + "WHERE cursor.streamKey = :streamKey "
            + "AND (cursor.workerLeaseUntil IS NULL OR cursor.workerLeaseUntil <= :now)")
    int tryClaimWorkerLease(@Param("streamKey") String streamKey,
                            @Param("owner") String owner,
                            @Param("leaseUntil") Instant leaseUntil,
                            @Param("now") Instant now);
}
