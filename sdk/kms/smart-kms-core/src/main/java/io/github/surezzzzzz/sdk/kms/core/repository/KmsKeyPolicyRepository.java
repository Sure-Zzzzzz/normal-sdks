package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;

import java.util.List;

/**
 * 精确 allow-only 密钥策略仓储端口。
 *
 * @author surezzzzzz
 */
public interface KmsKeyPolicyRepository {

    List<KmsKeyPolicy> findByKeyRef(String ownerPrincipalId, String keyRef);

    KmsKeyPolicy save(String ownerPrincipalId, KmsKeyPolicy policy);

    void revoke(String ownerPrincipalId, String keyRef, String policyId, long expectedRowVersion);
}
