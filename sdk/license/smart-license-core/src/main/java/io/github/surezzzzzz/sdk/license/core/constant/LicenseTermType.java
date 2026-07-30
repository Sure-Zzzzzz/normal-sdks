package io.github.surezzzzzz.sdk.license.core.constant;

import lombok.Getter;

/**
 * v1 标准 License 条款类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum LicenseTermType {

    /**
     * 功能集合条款。
     */
    FEATURE_SET(SmartLicenseCoreConstant.TERM_TYPE_FEATURE_SET, "功能集合"),
    /**
     * 容量条款。
     */
    CAPACITY(SmartLicenseCoreConstant.TERM_TYPE_CAPACITY, "容量限制"),
    /**
     * 试用条款。
     */
    TRIAL(SmartLicenseCoreConstant.TERM_TYPE_TRIAL, "试用期限");

    private final String code;
    private final String description;

    LicenseTermType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取条款类型。
     *
     * @param code 条款类型代码
     * @return 条款类型；不存在时返回 null
     */
    public static LicenseTermType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (LicenseTermType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断条款类型代码是否有效。
     *
     * @param code 条款类型代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部条款类型代码。
     *
     * @return 条款类型代码数组
     */
    public static String[] getAllCodes() {
        LicenseTermType[] types = values();
        String[] codes = new String[types.length];
        for (int index = SmartLicenseCoreConstant.ZERO; index < types.length; index++) {
            codes[index] = types[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定条款类型代码。
     *
     * @return 条款类型代码
     */
    @Override
    public String toString() {
        return code;
    }
}
