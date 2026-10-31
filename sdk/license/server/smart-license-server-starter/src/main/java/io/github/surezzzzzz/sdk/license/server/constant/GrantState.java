package io.github.surezzzzzz.sdk.license.server.constant;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;

/**
 * 授权管理状态：DRAFT/ENABLED/SUSPENDED/REVOKED。
 *
 * <p>REVOKED 只能经不可撤回吊销产生；暂停/恢复经 grantStateVersion 单调递增围栏。</p>
 *
 * @author surezzzzzz
 */
public enum GrantState {

    DRAFT("DRAFT", "草稿"),
    ENABLED("ENABLED", "生效"),
    SUSPENDED("SUSPENDED", "暂停"),
    REVOKED("REVOKED", "吊销");

    private final String code;
    private final String description;

    GrantState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static GrantState fromCode(String code) {
        for (GrantState value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(ErrorMessage.PROTOCOL_FIELD_INVALID);
    }

    public static boolean isValid(String code) {
        for (GrantState value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static String[] getAllCodes() {
        GrantState[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].code;
        }
        return codes;
    }
}
