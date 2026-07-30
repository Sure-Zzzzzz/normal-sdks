package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.support.LicenseValidationHelper;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * License 签发命令。
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
public final class LicenseIssueCommand {

    private final String tenantId;
    private final String kid;
    private final LicenseClaims claims;

    /**
     * 创建签发命令。
     *
     * @param tenantId tenant 标识
     * @param kid      业务密钥标识
     * @param claims   已验证的 v1 声明
     */
    @Builder
    public LicenseIssueCommand(String tenantId, String kid, LicenseClaims claims) {
        this.tenantId = LicenseValidationHelper.requireText(tenantId, SmartLicenseCoreConstant.FIELD_TENANT_ID,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        this.kid = LicenseValidationHelper.requireText(kid, SmartLicenseCoreConstant.FIELD_KEY_ID,
                SmartLicenseCoreConstant.MAX_IDENTIFIER_LENGTH);
        if (claims == null) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_TERMS);
        }
        if (!this.tenantId.equals(claims.getTenantId())) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.DETAIL_MISMATCH);
        }
        this.claims = claims;
    }
}
