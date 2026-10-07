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

    /**
     * 按已验证 DataPlan 归属范围和精确归属主体查询销毁任务；归属筛选只能在范围内收窄。
     *
     * <p>默认实现不支持归属筛选：自定义仓储未覆盖本方法时，携带归属筛选的请求返回 400，
     * 不做内存过滤以免分页与总数失真；内置 JDBC 仓储已覆盖。</p>
     *
     * @param scope            已验证的数据访问范围
     * @param ownerPrincipalId 可选精确归属主体；为空时等价于不带归属筛选
     * @param offset           从零开始的分页偏移
     * @param limit            当前页最大记录数
     * @return 无材料任务分页投影
     */
    default KmsDestructionJobPage findPage(KmsOwnerAccessScope scope, String ownerPrincipalId, long offset, int limit) {
        if (ownerPrincipalId == null) {
            return findPage(scope, offset, limit);
        }
        throw new io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException();
    }
}
