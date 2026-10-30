package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;

/**
 * 凭据种类：离线文件或混合租约；纯 ONLINE 不发此形态凭据。
 *
 * @author surezzzzzz
 */
public enum CredentialKind {

    OFFLINE("OFFLINE", "离线文件"),
    HYBRID("HYBRID", "混合租约");

    private final String code;
    private final String description;

    CredentialKind(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static CredentialKind fromCode(String code) {
        for (CredentialKind value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(ErrorMessage.PROTOCOL_FIELD_INVALID);
    }

    public static boolean isValid(String code) {
        for (CredentialKind value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static String[] getAllCodes() {
        return new String[]{OFFLINE.code, HYBRID.code};
    }
}
