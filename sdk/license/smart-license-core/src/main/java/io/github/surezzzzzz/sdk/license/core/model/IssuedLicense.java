package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.support.LicenseBase64UrlHelper;
import io.github.surezzzzzz.sdk.license.core.support.LicenseValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 已签发的 Compact JWS 投影。
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
public final class IssuedLicense {

    private final String compactJws;
    private final String kid;
    private final int kmsKeyVersion;

    /**
     * 创建已签发 License 投影。
     *
     * @param compactJws    Compact JWS
     * @param kid           业务密钥标识
     * @param kmsKeyVersion 实际 KMS 签名版本
     */
    public IssuedLicense(String compactJws, String kid, int kmsKeyVersion) {
        this.compactJws = LicenseValidationHelper.requireText(compactJws, SmartLicenseCoreConstant.FIELD_COMPACT_JWS,
                SmartLicenseCoreConstant.MAX_COMPACT_JWS_LENGTH);
        validateCompactJws(this.compactJws);
        this.kid = LicenseValidationHelper.requireText(kid, SmartLicenseCoreConstant.FIELD_KEY_ID,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        if (kmsKeyVersion < SmartLicenseCoreConstant.MIN_POSITIVE_INTEGER) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_KMS_KEY_VERSION);
        }
        this.kmsKeyVersion = kmsKeyVersion;
    }

    private static void validateCompactJws(String value) {
        String[] segments = value.split("\\.", -1);
        if (segments.length != SmartLicenseCoreConstant.JWS_SEGMENT_COUNT
                || !LicenseBase64UrlHelper.isUnpadded(segments[SmartLicenseCoreConstant.ZERO])
                || !LicenseBase64UrlHelper.isUnpadded(segments[SmartLicenseCoreConstant.ONE])
                || !LicenseBase64UrlHelper.isUnpadded(
                segments[SmartLicenseCoreConstant.JWS_SEGMENT_COUNT - SmartLicenseCoreConstant.ONE])
                || segments[SmartLicenseCoreConstant.JWS_SEGMENT_COUNT - SmartLicenseCoreConstant.ONE].length()
                != SmartLicenseCoreConstant.ES256_JOSE_SIGNATURE_BASE64URL_LENGTH) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_COMPACT_JWS);
        }
    }
}
