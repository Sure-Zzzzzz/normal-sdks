package io.github.surezzzzzz.sdk.mail.constant;

import lombok.Getter;

/**
 * Mail Provider 类型
 *
 * @author surezzzzzz
 */
@Getter
public enum MailProviderType {

    NORMAL("normal", "普通 SMTP");

    private final String code;
    private final String description;

    MailProviderType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static MailProviderType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MailProviderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    public static String[] getAllCodes() {
        MailProviderType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
