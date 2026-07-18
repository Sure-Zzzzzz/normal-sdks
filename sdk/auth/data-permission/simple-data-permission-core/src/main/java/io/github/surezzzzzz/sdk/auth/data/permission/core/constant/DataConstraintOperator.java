package io.github.surezzzzzz.sdk.auth.data.permission.core.constant;

import lombok.Getter;

/**
 * 数据约束操作符。
 *
 * @author surezzzzzz
 */
@Getter
public enum DataConstraintOperator {

    /**
     * 值包含于授权值集合。
     */
    IN("IN", "包含于");

    /**
     * 操作符代码。
     */
    private final String code;
    /**
     * 操作符说明。
     */
    private final String description;

    DataConstraintOperator(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取操作符。
     *
     * @param code 操作符代码
     * @return 操作符；不存在时返回 null
     */
    public static DataConstraintOperator fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DataConstraintOperator operator : values()) {
            if (operator.code.equals(code)) {
                return operator;
            }
        }
        return null;
    }

    /**
     * 判断操作符代码是否有效。
     *
     * @param code 操作符代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部操作符代码。
     *
     * @return 操作符代码数组
     */
    public static String[] getAllCodes() {
        DataConstraintOperator[] operators = values();
        String[] codes = new String[operators.length];
        for (int index = 0; index < operators.length; index++) {
            codes[index] = operators[index].code;
        }
        return codes;
    }

    /**
     * 返回操作符代码。
     *
     * @return 操作符代码
     */
    @Override
    public String toString() {
        return code;
    }
}
