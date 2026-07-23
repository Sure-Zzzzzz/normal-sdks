package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;

import java.util.List;
import java.util.Optional;

/**
 * 密钥版本仓储端口。
 *
 * <p>版本材料只能在 KMS 可信边界内由本端口的服务端适配实现读取和写入，不能转换为对外模型。</p>
 *
 * @author surezzzzzz
 */
public interface KmsKeyVersionRepository {

    /**
     * 查询逻辑密钥的精确版本。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @param version  版本号
     * @return 匹配的版本；不存在时为空
     */
    Optional<KmsKeyVersion> findByVersion(String tenantId, String keyRef, int version);

    /**
     * 查询逻辑密钥的全部版本。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 版本集合
     */
    List<KmsKeyVersion> findByKeyRef(String tenantId, String keyRef);

    /**
     * 保存密钥版本快照。
     *
     * @param tenantId   资源所属 tenant
     * @param keyVersion 待保存的版本
     * @return 已持久化的版本快照
     */
    KmsKeyVersion save(String tenantId, KmsKeyVersion keyVersion);
}
