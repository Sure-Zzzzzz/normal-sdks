package io.github.surezzzzzz.sdk.license.core.constant;

import lombok.Getter;

/**
 * License 业务密钥映射状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum LicenseKeyStatus {

    /**
     * 可用于新签发。
     */
    ACTIVE(SmartLicenseCoreConstant.KMS_KEY_STATE_ACTIVE, "可用于新签发"),
    /**
     * 仅保留历史 License 验证。
     */
    RETIRED(SmartLicenseCoreConstant.KMS_KEY_STATE_RETIRED, "仅保留历史验证");

    private final String code;
    private final String description;

    LicenseKeyStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取状态。
     *
     * @param code 状态代码
     * @return 状态；不存在时返回 null
     */
    public static LicenseKeyStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (LicenseKeyStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断状态代码是否有效。
     *
     * @param code 状态代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部状态代码。
     *
     * @return 状态代码数组
     */
    public static String[] getAllCodes() {
        LicenseKeyStatus[] statuses = values();
        String[] codes = new String[statuses.length];
        for (int index = SmartLicenseCoreConstant.ZERO; index < statuses.length; index++) {
            codes[index] = statuses[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定状态代码。
     *
     * @return 状态代码
     */
    @Override
    public String toString() {
        return code;
    }
}
