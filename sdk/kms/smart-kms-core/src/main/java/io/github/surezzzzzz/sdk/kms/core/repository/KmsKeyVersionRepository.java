package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;

import java.util.List;
import java.util.Optional;

/**
 * 密钥版本仓储端口。材料只在 KMS 可信边界内由服务端适配器读取和写入。
 *
 * @author surezzzzzz
 */
public interface KmsKeyVersionRepository {

    Optional<KmsKeyVersion> findByVersion(String ownerPrincipalId, String keyRef, int version);

    List<KmsKeyVersion> findByKeyRef(String ownerPrincipalId, String keyRef);

    KmsKeyVersion save(String ownerPrincipalId, KmsKeyVersion keyVersion);
}
