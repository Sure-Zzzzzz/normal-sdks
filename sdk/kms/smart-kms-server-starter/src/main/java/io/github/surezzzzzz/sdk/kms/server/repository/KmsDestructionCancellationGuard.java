package io.github.surezzzzzz.sdk.kms.server.repository;

/**
 * 销毁取消历史领取事实检查端口。
 *
 * @author surezzzzzz
 */
public interface KmsDestructionCancellationGuard {

    /**
     * 在已锁定同一逻辑密钥的事务中确认所有销毁任务从未被领取。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 所有销毁任务从未被成功领取时返回 {@code true}
     */
    boolean areAllJobsUnclaimed(String tenantId, String keyRef);

    /**
     * 删除已经确认从未领取的逻辑密钥全部销毁任务。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     */
    void deleteUnclaimedJobs(String tenantId, String keyRef);
}
