package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.LicenseKeyStatus;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.support.LicenseValidationHelper;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;

/**
 * tenant 与业务 kid 的不可变 KMS 密钥映射。
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
public final class LicenseKeyMapping {

    private final String tenantId;
    private final String kid;
    private final String kmsKeyRef;
    private final int kmsKeyVersion;
    private final String algorithm;
    private final LicenseKeyStatus status;
    private final byte[] publicKey;

    /**
     * 创建不可变业务密钥映射。
     *
     * @param tenantId      tenant 标识
     * @param kid           业务密钥标识
     * @param kmsKeyRef     KMS 逻辑密钥标识
     * @param kmsKeyVersion KMS 精确版本
     * @param algorithm     密码算法
     * @param publicKey     X.509 SubjectPublicKeyInfo DER 公钥
     * @param status        映射状态
     */
    @Builder
    public LicenseKeyMapping(String tenantId, String kid, String kmsKeyRef, int kmsKeyVersion, String algorithm,
                             byte[] publicKey, LicenseKeyStatus status) {
        this.tenantId = LicenseValidationHelper.requireText(tenantId, SmartLicenseCoreConstant.FIELD_TENANT_ID,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        this.kid = LicenseValidationHelper.requireText(kid, SmartLicenseCoreConstant.FIELD_KEY_ID,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        this.kmsKeyRef = LicenseValidationHelper.requireText(kmsKeyRef, SmartLicenseCoreConstant.FIELD_KMS_KEY_REF,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        if (kmsKeyVersion < SmartLicenseCoreConstant.MIN_POSITIVE_INTEGER) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_KMS_KEY_VERSION);
        }
        this.kmsKeyVersion = kmsKeyVersion;
        if (!SmartLicenseCoreConstant.ALGORITHM_ES256.equals(algorithm)) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_ALGORITHM);
        }
        this.algorithm = algorithm;
        if (publicKey == null || publicKey.length == SmartLicenseCoreConstant.ZERO) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_PUBLIC_KEY);
        }
        this.publicKey = Arrays.copyOf(publicKey, publicKey.length);
        if (status == null) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_KEY_MAPPING);
        }
        this.status = status;
    }

    /**
     * 获取公钥材料副本。
     *
     * @return X.509 SubjectPublicKeyInfo DER 公钥副本
     */
    public byte[] getPublicKey() {
        return Arrays.copyOf(publicKey, publicKey.length);
    }
}
