package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

import lombok.Getter;

/**
 * 一元运算符枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum UnaryOperator {

    /**
     * 逻辑非（取反）
     */
    NOT("逻辑非");

    private final String description;

    UnaryOperator(String description) {
        this.description = description;
    }

    /** 稳定英文代码。 */
    public String getCode() { return name(); }
    /** 未知码返回 null。 */
    public static UnaryOperator fromCode(String code) {
        for (UnaryOperator value : values()) if (value.name().equals(code)) return value;
        return null;
    }
    /** 判断已知代码。 */
    public static boolean isValid(String code) { return fromCode(code) != null; }
    /** 返回独立代码数组。 */
    public static String[] getAllCodes() {
        return java.util.Arrays.stream(values()).map(UnaryOperator::getCode).toArray(String[]::new);
    }
    /** 默认只输出稳定代码。 */
    @Override
    public String toString() { return getCode(); }
}
