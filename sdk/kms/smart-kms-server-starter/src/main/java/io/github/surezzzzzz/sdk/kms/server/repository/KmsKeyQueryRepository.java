package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;

import java.util.List;
import java.util.Optional;

/**
 * KMS 管理列表内部只读仓储端口。
 *
 * @author surezzzzzz
 */
public interface KmsKeyQueryRepository {

    /**
     * 查询当前 owner 下单个逻辑密钥元数据。
     *
     * @param ownerPrincipalId 资源所属 owner
     * @param keyRef           逻辑密钥标识
     * @return 无材料密钥元数据；不存在时为空
     */
    Optional<KmsKeyMetadata> findMetadata(String ownerPrincipalId, String keyRef);

    /**
     * 按完整的已验证归属范围查询单个逻辑密钥元数据。
     *
     * @param scope  已评估 DataPlan 的 KMS 归属投影
     * @param keyRef 逻辑密钥标识
     * @return 命中且在范围内的密钥；无权与不存在均为空
     */
    Optional<KmsKeyMetadata> findMetadata(KmsOwnerAccessScope scope, String keyRef);

    /**
     * 查询当前 owner 下全部逻辑密钥元数据。
     *
     * @param ownerPrincipalId 资源所属 owner
     * @return 按更新时间倒序和标识升序稳定排序的无材料密钥元数据
     */
    List<KmsKeyMetadata> findAllMetadata(String ownerPrincipalId);

    /**
     * 按 owner、筛选条件和稳定排序读取一页无材料密钥元数据。
     *
     * @param ownerPrincipalId 资源所属 owner
     * @param alias            可选别名片段
     * @param purpose          可选用途编码
     * @param algorithm        可选算法编码
     * @param state            可选状态编码
     * @param offset           从零开始的结果偏移量
     * @param size             当前页最大记录数
     * @return 当前页与筛选后总数
     */
    KmsKeyPage findPage(String ownerPrincipalId, String alias, String purpose, String algorithm, String state, long offset,
                        int size);

    /**
     * 按完整的已验证归属范围分页查询逻辑密钥。
     *
     * @param scope     已评估 DataPlan 的 KMS 归属投影
     * @param alias     可选别名片段
     * @param purpose   可选用途编码
     * @param algorithm 可选算法编码
     * @param state     可选状态编码
     * @param offset    从零开始的结果偏移量
     * @param size      当前页最大记录数
     * @return 当前页与同一范围内的总数
     */
    KmsKeyPage findPage(KmsOwnerAccessScope scope, String alias, String purpose, String algorithm, String state,
                        long offset, int size);

    /**
     * 按完整的已验证归属范围和精确归属主体分页查询逻辑密钥；归属筛选只能在 DataPlan 范围内收窄。
     *
     * <p>默认实现不支持归属筛选：自定义仓储未覆盖本方法时，携带归属筛选的请求返回 400，
     * 不做内存过滤以免分页与总数失真；内置 JDBC 仓储已覆盖。</p>
     *
     * @param scope            已评估 DataPlan 的 KMS 归属投影
     * @param alias            可选别名片段
     * @param purpose          可选用途编码
     * @param algorithm        可选算法编码
     * @param state            可选状态编码
     * @param ownerPrincipalId 可选精确归属主体；为空时等价于不带归属筛选
     * @param offset           从零开始的结果偏移量
     * @param size             当前页最大记录数
     * @return 当前页与同一范围内筛选后的总数
     */
    default KmsKeyPage findPage(KmsOwnerAccessScope scope, String alias, String purpose, String algorithm, String state,
                                String ownerPrincipalId, long offset, int size) {
        if (ownerPrincipalId == null) {
            return findPage(scope, alias, purpose, algorithm, state, offset, size);
        }
        throw new io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException();
    }
}
