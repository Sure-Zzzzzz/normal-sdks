package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;

/**
 * 比较运算符：EQ/NE 同型通用，LT/LTE/GT/GTE 仅整数。
 *
 * @author surezzzzzz
 */
public enum CompareOperator {

    EQ("EQ"),
    NE("NE"),
    LT("LT"),
    LTE("LTE"),
    GT("GT"),
    GTE("GTE");

    private final String code;

    CompareOperator(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 是否为仅整数可用的次序比较。 */
    public boolean isOrderOperator() {
        return this == LT || this == LTE || this == GT || this == GTE;
    }

    public static CompareOperator fromCode(String code) {
        for (CompareOperator value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
    }

    public static boolean isValid(String code) {
        for (CompareOperator value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static String[] getAllCodes() {
        CompareOperator[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].code;
        }
        return codes;
    }
}
