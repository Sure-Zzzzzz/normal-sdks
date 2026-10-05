package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

import lombok.Getter;

/**
 * 逻辑运算符枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum LogicalOperator {

    /**
     * 逻辑与（所有条件同时满足）
     */
    AND("且"),

    /**
     * 逻辑或（任一条件满足即可）
     */
    OR("或");

    private final String description;

    LogicalOperator(String description) {
        this.description = description;
    }

    /** 稳定英文代码。 */
    public String getCode() { return name(); }
    /** 未知码返回 null。 */
    public static LogicalOperator fromCode(String code) {
        for (LogicalOperator value : values()) if (value.name().equals(code)) return value;
        return null;
    }
    /** 判断已知代码。 */
    public static boolean isValid(String code) { return fromCode(code) != null; }
    /** 返回独立代码数组。 */
    public static String[] getAllCodes() {
        return java.util.Arrays.stream(values()).map(LogicalOperator::getCode).toArray(String[]::new);
    }
    /** 默认只输出稳定代码。 */
    @Override
    public String toString() { return getCode(); }
}
