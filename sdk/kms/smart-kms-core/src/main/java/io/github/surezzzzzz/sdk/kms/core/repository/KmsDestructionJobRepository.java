package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 销毁任务仓储端口。领取令牌始终与全局 keyRef 和版本联合条件更新。
 *
 * @author surezzzzzz
 */
public interface KmsDestructionJobRepository {

    KmsDestructionJob save(String ownerPrincipalId, KmsDestructionJob job);

    List<KmsDestructionJob> findByKeyRef(String ownerPrincipalId, String keyRef);

    List<KmsDestructionJob> findDueOrExpiredClaim(Instant now);

    boolean claim(String ownerPrincipalId, String keyRef, int keyVersion, String claimToken, Instant claimUntil, Instant now);

    boolean renewClaim(String ownerPrincipalId, String keyRef, int keyVersion, String claimToken, Instant claimUntil, Instant now);

    boolean release(String ownerPrincipalId, String keyRef, int keyVersion, String claimToken);

    boolean complete(String ownerPrincipalId, String keyRef, int keyVersion, String claimToken, Instant completedAt);

    Optional<Duration> findOldestOverdueDelay(Instant now);
}
