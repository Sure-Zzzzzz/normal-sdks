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
 * <p>策略只允许精确匹配 tenant、主体、逻辑密钥和操作；不支持通配符。
 * {@code keyVersion} 为 {@code null} 时表示匹配该逻辑密钥的任意版本。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsKeyPolicy {

    /**
     * 策略稳定标识。
     */
    private final String policyId;
    /**
     * 策略所属 tenant。
     */
    private final String tenantId;
    /**
     * 被授权的逻辑密钥标识。
     */
    private final String keyRef;
    /**
     * 被精确授权的主体标识。
     */
    private final String principalId;
    /**
     * 被授权的精确版本；为 {@code null} 时不限制版本。
     */
    private final Integer keyVersion;
    /**
     * 被允许执行的单一 KMS 操作。
     */
    private final KmsOperation operation;
    /**
     * 授权到期时间；为 {@code null} 时不设置到期限制。
     */
    private final Instant expiresAt;
    /**
     * 持久化层乐观锁版本。
     */
    private final long rowVersion;

    /**
     * 创建精确 allow-only 密钥策略。
     *
     * @param policyId    策略稳定标识
     * @param tenantId    策略所属 tenant
     * @param keyRef      被授权的逻辑密钥标识
     * @param principalId 被授权主体标识
     * @param keyVersion  被授权的精确版本
     * @param operation   被允许执行的密码学或公钥读取操作
     * @param expiresAt   授权到期时间
     * @param rowVersion  持久化层乐观锁版本
     */
    @Builder
    public KmsKeyPolicy(String policyId, String tenantId, String keyRef, String principalId,
                        Integer keyVersion, KmsOperation operation, Instant expiresAt, long rowVersion) {
        this.policyId = KmsValidationHelper.requirePolicyId(policyId);
        this.tenantId = KmsValidationHelper.requireTenantId(tenantId);
        this.keyRef = KmsValidationHelper.requireKeyRef(keyRef);
        this.principalId = KmsValidationHelper.requirePrincipalId(principalId);
        if (keyVersion != null && keyVersion.intValue() <= SmartKmsCoreConstant.ZERO) {
            throw new KmsValidationException();
        }
        if (!isPolicyOperation(operation)) {
            throw new KmsValidationException();
        }
        this.keyVersion = keyVersion;
        this.operation = operation;
        this.expiresAt = expiresAt;
        this.rowVersion = rowVersion;
    }

    private static boolean isPolicyOperation(KmsOperation operation) {
        return operation == KmsOperation.SIGN
                || operation == KmsOperation.VERIFY
                || operation == KmsOperation.ENCRYPT
                || operation == KmsOperation.DECRYPT
                || operation == KmsOperation.READ_PUBLIC_KEY;
    }
}
