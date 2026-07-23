package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;

import java.util.List;

/**
 * 密钥策略仓储端口。
 *
 * <p>密码学操作与策略撤销必须在服务端适配层对同一逻辑密钥使用共享线性化锁，避免撤销与执行交错。</p>
 *
 * @author surezzzzzz
 */
public interface KmsKeyPolicyRepository {

    /**
     * 查询逻辑密钥下的全部精确策略。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 策略集合
     */
    List<KmsKeyPolicy> findByKeyRef(String tenantId, String keyRef);

    /**
     * 保存精确 allow-only 策略。
     *
     * @param tenantId 资源所属 tenant
     * @param policy   待保存的策略
     * @return 已持久化的策略快照
     */
    KmsKeyPolicy save(String tenantId, KmsKeyPolicy policy);

    /**
     * 按乐观锁版本撤销策略。
     *
     * @param tenantId           资源所属 tenant
     * @param keyRef             逻辑密钥标识
     * @param policyId           策略标识
     * @param expectedRowVersion 预期乐观锁版本
     */
    void revoke(String tenantId, String keyRef, String policyId, long expectedRowVersion);
}
