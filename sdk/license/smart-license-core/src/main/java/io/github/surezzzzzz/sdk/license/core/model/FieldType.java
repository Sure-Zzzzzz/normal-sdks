package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;

/**
 * 模型字段类型：严格四型，不接受隐式转换。
 *
 * @author surezzzzzz
 */
public enum FieldType {

    /** JSON 整数，安全整数范围，不接受小数、字符串、布尔或 null。 */
    INTEGER("INTEGER", "整数"),

    /** JSON 布尔，不接受文本真值、0/1 或空值。 */
    BOOLEAN("BOOLEAN", "布尔"),

    /** Unicode 文本，不做隐式 trim、大小写或归一化。 */
    STRING("STRING", "文本"),

    /** 无重复文本数组，成员精确比较。 */
    STRING_SET("STRING_SET", "文本集合");

    private final String code;
    private final String description;

    FieldType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static FieldType fromCode(String code) {
        for (FieldType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
    }

    public static boolean isValid(String code) {
        for (FieldType value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static String[] getAllCodes() {
        FieldType[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].code;
        }
        return codes;
    }
}
