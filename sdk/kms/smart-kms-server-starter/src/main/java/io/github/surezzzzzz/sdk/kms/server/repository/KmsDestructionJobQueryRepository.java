package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;

/**
 * 销毁任务管理页查询端口。
 *
 * <p>该端口只承载当前 owner 的无材料分页投影，不参与 worker 领取、续租、完成等领域写链。</p>
 *
 * @author surezzzzzz
 */
public interface KmsDestructionJobQueryRepository {

    /**
     * 查询当前 owner 的销毁任务。
     *
     * @param ownerPrincipalId 当前认证主体唯一 owner
     * @param offset           从零开始的分页偏移
     * @param limit            当前页最大记录数
     * @return 无材料任务分页投影
     */
    KmsDestructionJobPage findPage(String ownerPrincipalId, long offset, int limit);

    /**
     * 按已评估 DataPlan 的归属范围查询销毁任务。
     *
     * @param scope  已验证的数据访问范围
     * @param offset 从零开始的分页偏移
     * @param limit  当前页最大记录数
     * @return 无材料任务分页投影
     */
    KmsDestructionJobPage findPage(KmsOwnerAccessScope scope, long offset, int limit);
}
