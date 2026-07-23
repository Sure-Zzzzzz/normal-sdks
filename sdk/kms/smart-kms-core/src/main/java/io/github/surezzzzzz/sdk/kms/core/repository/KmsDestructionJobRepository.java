package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 销毁任务仓储端口。
 *
 * <p>领取、续租、释放和完成必须以 tenant、keyRef、版本及领取令牌做条件更新，防止租约过期 worker
 * 覆盖新领取者的状态。所有任务写入、状态变更和统计均不承载密码材料。</p>
 *
 * @author surezzzzzz
 */
public interface KmsDestructionJobRepository {

    /**
     * 保存销毁任务。
     *
     * @param tenantId 资源所属 tenant
     * @param job      待保存的无材料任务
     * @return 已持久化的任务快照
     */
    KmsDestructionJob save(String tenantId, KmsDestructionJob job);

    /**
     * 查询逻辑密钥的全部销毁任务，用于取消销毁前确认没有任务已被成功领取。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 该逻辑密钥的销毁任务集合
     */
    List<KmsDestructionJob> findByKeyRef(String tenantId, String keyRef);

    /**
     * 查询已到期或领取租约已过期的销毁任务。
     *
     * @param now 权威当前时间
     * @return 可尝试领取的任务集合
     */
    List<KmsDestructionJob> findDueOrExpiredClaim(Instant now);

    /**
     * 条件领取销毁任务。
     *
     * @param tenantId   资源所属 tenant
     * @param keyRef     逻辑密钥标识
     * @param keyVersion 密钥版本号
     * @param claimToken 本次领取的唯一令牌
     * @param claimUntil 本次领取的租约到期时间
     * @param now        权威当前时间
     * @return 成功取得任务租约时返回 {@code true}
     */
    boolean claim(String tenantId, String keyRef, int keyVersion, String claimToken,
                  Instant claimUntil, Instant now);

    /**
     * 使用当前领取令牌续租销毁任务。
     *
     * @param tenantId   资源所属 tenant
     * @param keyRef     逻辑密钥标识
     * @param keyVersion 密钥版本号
     * @param claimToken 当前 worker 的领取令牌
     * @param claimUntil 新的领取租约到期时间
     * @param now        权威当前时间
     * @return 租约仍归当前 worker 且续租成功时返回 {@code true}
     */
    boolean renewClaim(String tenantId, String keyRef, int keyVersion, String claimToken,
                       Instant claimUntil, Instant now);

    /**
     * 使用当前领取令牌释放尚未完成的销毁任务。
     *
     * @param tenantId   资源所属 tenant
     * @param keyRef     逻辑密钥标识
     * @param keyVersion 密钥版本号
     * @param claimToken 当前 worker 的领取令牌
     * @return 成功释放当前 worker 领取的任务时返回 {@code true}
     */
    boolean release(String tenantId, String keyRef, int keyVersion, String claimToken);

    /**
     * 以领取令牌条件完成销毁任务。
     *
     * @param tenantId    资源所属 tenant
     * @param keyRef      逻辑密钥标识
     * @param keyVersion  密钥版本号
     * @param claimToken  本次领取令牌
     * @param completedAt 成功完成时间
     * @return 当前 worker 成功完成自己领取的任务时返回 {@code true}
     */
    boolean complete(String tenantId, String keyRef, int keyVersion, String claimToken,
                     Instant completedAt);

    /**
     * 查询最早已到期但未完成的销毁任务延迟。
     *
     * @param now 权威当前时间
     * @return 有逾期任务时的最早延迟；没有逾期任务时为空
     */
    Optional<Duration> findOldestOverdueDelay(Instant now);
}
