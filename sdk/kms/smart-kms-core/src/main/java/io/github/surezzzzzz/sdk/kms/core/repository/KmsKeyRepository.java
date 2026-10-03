package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;

import java.util.Optional;

/**
 * 逻辑密钥仓储端口。keyRef 全局唯一，归属由密钥记录自身持久化。
 *
 * @author surezzzzzz
 */
public interface KmsKeyRepository {

    Optional<KmsKey> findByKeyRef(String ownerPrincipalId, String keyRef);

    KmsKey save(String ownerPrincipalId, KmsKey key);
}
