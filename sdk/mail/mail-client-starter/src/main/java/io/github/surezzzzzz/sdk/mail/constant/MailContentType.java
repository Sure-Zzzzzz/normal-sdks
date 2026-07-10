package io.github.surezzzzzz.sdk.mail.constant;

import lombok.Getter;

/**
 * Mail 内容类型
 *
 * @author surezzzzzz
 */
@Getter
public enum MailContentType {

    TEXT("text", "文本"),
    HTML("html", "HTML");

    private final String code;
    private final String description;

    MailContentType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static MailContentType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MailContentType type : values()) {
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
        MailContentType[] types = values();
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
