package io.github.surezzzzzz.sdk.kms.server.repository;

import java.util.List;
import java.util.Optional;

/**
 * KMS 管理列表内部只读仓储端口。
 *
 * @author surezzzzzz
 */
public interface KmsKeyQueryRepository {

    /**
     * 查询当前 tenant 下单个逻辑密钥元数据。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 无材料密钥元数据；不存在时为空
     */
    Optional<KmsKeyMetadata> findMetadata(String tenantId, String keyRef);

    /**
     * 查询当前 tenant 下全部逻辑密钥元数据。
     *
     * @param tenantId 资源所属 tenant
     * @return 按更新时间倒序和标识升序稳定排序的无材料密钥元数据
     */
    List<KmsKeyMetadata> findAllMetadata(String tenantId);

    /**
     * 按 tenant、筛选条件和稳定排序读取一页无材料密钥元数据。
     *
     * @param tenantId  资源所属 tenant
     * @param alias     可选别名片段
     * @param purpose   可选用途编码
     * @param algorithm 可选算法编码
     * @param state     可选状态编码
     * @param offset    从零开始的结果偏移量
     * @param size      当前页最大记录数
     * @return 当前页与筛选后总数
     */
    KmsKeyPage findPage(String tenantId, String alias, String purpose, String algorithm, String state, long offset,
                        int size);
}
