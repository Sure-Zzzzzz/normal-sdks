package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionWorkerState;

import java.time.Instant;
import java.util.Optional;

/**
 * 销毁 worker 状态仓储端口。
 *
 * <p>只记录服务健康所需的成功和失败事实，不持久化异常链、密码材料或任务敏感内容。</p>
 *
 * @author surezzzzzz
 */
public interface KmsDestructionWorkerStateRepository {

    /**
     * 查询 worker 持久化运行状态。
     *
     * @param instanceId worker 实例标识
     * @return 已保存的运行状态；首次运行时为空
     */
    Optional<KmsDestructionWorkerState> findByInstanceId(String instanceId);

    /**
     * 记录一次成功扫描，并将连续失败次数归零。
     *
     * @param instanceId worker 实例标识
     * @param scannedAt  成功扫描时间
     */
    void recordSuccess(String instanceId, Instant scannedAt);

    /**
     * 记录一次扫描或执行失败事实，并递增连续失败次数。
     *
     * @param instanceId worker 实例标识
     * @param failedAt   失败发生时间
     */
    void recordFailure(String instanceId, Instant failedAt);
}
