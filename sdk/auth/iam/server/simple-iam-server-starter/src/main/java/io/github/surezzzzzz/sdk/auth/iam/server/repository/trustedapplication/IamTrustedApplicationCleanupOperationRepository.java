package io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationCleanupOperationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.TrustedApplicationCleanupOperationState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 可信应用删除操作 Repository。
 *
 * @author surezzzzzz
 */
@Repository
public interface IamTrustedApplicationCleanupOperationRepository
        extends JpaRepository<IamTrustedApplicationCleanupOperationEntity, Long> {

    Optional<IamTrustedApplicationCleanupOperationEntity> findFirstByApplicationIdAndStateInOrderByIdDesc(
            Long applicationId, Collection<TrustedApplicationCleanupOperationState> states);

    @Query("SELECT operation FROM IamTrustedApplicationCleanupOperationEntity operation "
            + "WHERE operation.state IN :readyStates "
            + "OR (operation.state = :runningState "
            + "AND (operation.leaseUntil IS NULL OR operation.leaseUntil < :now)) "
            + "ORDER BY operation.id ASC")
    List<IamTrustedApplicationCleanupOperationEntity> findProcessableOperations(
            @Param("readyStates") Collection<TrustedApplicationCleanupOperationState> readyStates,
            @Param("runningState") TrustedApplicationCleanupOperationState runningState,
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * 以条件更新原子领取操作。只有一个实例能把待处理或已过期租约的记录推进为 RUNNING。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE IamTrustedApplicationCleanupOperationEntity operation "
            + "SET operation.state = :runningState, operation.attemptCount = operation.attemptCount + 1, "
            + "operation.leaseUntil = :leaseUntil, operation.leaseOwner = :leaseOwner, operation.updatedAt = :now, "
            + "operation.version = operation.version + 1 "
            + "WHERE operation.id = :operationId AND (operation.state IN :readyStates "
            + "OR (operation.state = :runningState AND "
            + "(operation.leaseUntil IS NULL OR operation.leaseUntil < :now)))")
    int claimForProcessing(@Param("operationId") Long operationId,
                           @Param("readyStates") Collection<TrustedApplicationCleanupOperationState> readyStates,
                           @Param("runningState") TrustedApplicationCleanupOperationState runningState,
                           @Param("now") Instant now,
                           @Param("leaseUntil") Instant leaseUntil,
                           @Param("leaseOwner") String leaseOwner);
}
