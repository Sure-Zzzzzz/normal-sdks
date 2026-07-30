package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.support.LicenseValidationHelper;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * License JWS v1 载荷声明。
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
public final class LicenseClaims {

    private final String jti;
    private final String issuer;
    private final String audience;
    private final Instant issuedAt;
    private final Instant notBefore;
    private final Instant expiresAt;
    private final int schemaVersion;
    private final String tenantId;
    private final String customerId;
    private final String deviceKeyFingerprint;
    private final List<LicenseTerm> terms;

    /**
     * 创建 v1 License 声明。
     *
     * @param jti                  License 唯一标识
     * @param issuer               签发 tenant 标识
     * @param audience             目标产品标识
     * @param issuedAt             签发时间
     * @param notBefore            生效时间
     * @param expiresAt            过期时间；永久 License 为 null
     * @param schemaVersion        协议版本
     * @param tenantId             tenant 标识
     * @param customerId           客户标识
     * @param deviceKeyFingerprint 设备公钥指纹
     * @param terms                已校验类型的 License 条款
     */
    @Builder
    public LicenseClaims(String jti, String issuer, String audience, Instant issuedAt, Instant notBefore,
                         Instant expiresAt, int schemaVersion, String tenantId, String customerId,
                         String deviceKeyFingerprint, Collection<LicenseTerm> terms) {
        this.jti = LicenseValidationHelper.requireText(jti, SmartLicenseCoreConstant.FIELD_JTI,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        this.issuer = LicenseValidationHelper.requireText(issuer, SmartLicenseCoreConstant.FIELD_ISSUER,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        this.audience = LicenseValidationHelper.requireText(audience, SmartLicenseCoreConstant.FIELD_AUDIENCE,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        this.issuedAt = requireInstant(issuedAt, SmartLicenseCoreConstant.FIELD_ISSUED_AT);
        this.notBefore = requireInstant(notBefore, SmartLicenseCoreConstant.FIELD_NOT_BEFORE);
        this.expiresAt = expiresAt;
        if (schemaVersion != SmartLicenseCoreConstant.ONE) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_SCHEMA_VERSION);
        }
        this.schemaVersion = schemaVersion;
        this.tenantId = LicenseValidationHelper.requireText(tenantId, SmartLicenseCoreConstant.FIELD_TENANT_ID,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        this.customerId = LicenseValidationHelper.requireText(customerId, SmartLicenseCoreConstant.FIELD_CUSTOMER_ID,
                SmartLicenseCoreConstant.MAX_CUSTOMER_ID_LENGTH);
        this.deviceKeyFingerprint = LicenseValidationHelper.requireText(deviceKeyFingerprint,
                SmartLicenseCoreConstant.FIELD_DEVICE_KEY_FINGERPRINT,
                SmartLicenseCoreConstant.MAX_DEVICE_FINGERPRINT_LENGTH);
        if (!this.issuer.equals(this.tenantId)) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.DETAIL_MISMATCH);
        }
        if (this.issuedAt.isAfter(this.notBefore)) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_ISSUED_AT);
        }
        if (this.expiresAt != null && !this.notBefore.isBefore(this.expiresAt)) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_EXPIRES_AT);
        }
        this.terms = copyTerms(terms);
    }

    private static Instant requireInstant(Instant value, String fieldName) {
        if (value == null) {
            throw LicenseValidationHelper.validation(String.format(SmartLicenseCoreConstant.DETAIL_CANNOT_BE_NULL,
                    fieldName));
        }
        return value;
    }

    private static List<LicenseTerm> copyTerms(Collection<LicenseTerm> values) {
        if (values == null) {
            throw LicenseValidationHelper.validation(String.format(SmartLicenseCoreConstant.DETAIL_CANNOT_BE_NULL,
                    SmartLicenseCoreConstant.FIELD_TERMS));
        }
        List<LicenseTerm> copied = new ArrayList<LicenseTerm>();
        for (LicenseTerm value : values) {
            if (value == null) {
                throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_TERMS);
            }
            LicenseValidationHelper.requireText(value.getType(), SmartLicenseCoreConstant.FIELD_TERM_TYPE,
                    SmartLicenseCoreConstant.MAX_TERM_TYPE_LENGTH);
            copied.add(value);
            if (copied.size() > SmartLicenseCoreConstant.MAX_TERM_COUNT) {
                throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_TERMS);
            }
        }
        return Collections.unmodifiableList(copied);
    }
}
