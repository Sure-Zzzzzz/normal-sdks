package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;

import java.util.Optional;

/**
 * 逻辑密钥仓储端口。
 *
 * <p>所有查询和写入均显式携带 tenant，适配层必须据此实施数据隔离及乐观锁语义。</p>
 *
 * @author surezzzzzz
 */
public interface KmsKeyRepository {

    /**
     * 按 tenant 和 keyRef 查询逻辑密钥。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 匹配的逻辑密钥；不存在时为空
     */
    Optional<KmsKey> findByKeyRef(String tenantId, String keyRef);

    /**
     * 保存逻辑密钥元数据。
     *
     * @param tenantId 资源所属 tenant
     * @param key      待保存的逻辑密钥
     * @return 已持久化的逻辑密钥快照
     */
    KmsKey save(String tenantId, KmsKey key);
}
