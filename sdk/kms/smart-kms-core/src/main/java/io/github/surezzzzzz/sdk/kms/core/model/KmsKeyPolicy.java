package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 精确 allow-only 密钥策略。
 *
 * <p>策略只允许精确匹配主体、逻辑密钥和操作；不支持通配、deny 或主体组展开。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsKeyPolicy {

    private final String policyId;
    private final String ownerPrincipalId;
    private final String keyRef;
    private final String principalId;
    private final Integer keyVersion;
    private final KmsOperation operation;
    private final Instant expiresAt;
    private final long rowVersion;

    @Builder
    public KmsKeyPolicy(String policyId, String ownerPrincipalId, String keyRef, String principalId, Integer keyVersion,
                        KmsOperation operation, Instant expiresAt, long rowVersion) {
        this.policyId = KmsValidationHelper.requirePolicyId(policyId);
        this.ownerPrincipalId = KmsValidationHelper.requirePrincipalId(ownerPrincipalId);
        this.keyRef = KmsValidationHelper.requireKeyRef(keyRef);
        this.principalId = KmsValidationHelper.requirePrincipalId(principalId);
        if (keyVersion != null && keyVersion.intValue() <= SmartKmsCoreConstant.ZERO) {
            throw new KmsValidationException();
        }
        if (!isPolicyOperation(operation) || rowVersion < 0L) {
            throw new KmsValidationException();
        }
        this.keyVersion = keyVersion;
        this.operation = operation;
        this.expiresAt = expiresAt;
        this.rowVersion = rowVersion;
    }

    private static boolean isPolicyOperation(KmsOperation operation) {
        return operation == KmsOperation.SIGN || operation == KmsOperation.VERIFY
                || operation == KmsOperation.ENCRYPT || operation == KmsOperation.DECRYPT
                || operation == KmsOperation.READ_PUBLIC_KEY;
    }
}
