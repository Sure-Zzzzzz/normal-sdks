package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

import lombok.Getter;

/**
 * 比较运算符枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum ComparisonOperator {

    /**
     * 等于
     */
    EQ("等于"),

    /**
     * 不等于
     */
    NE("不等于"),

    /**
     * 大于
     */
    GT("大于"),

    /**
     * 大于等于
     */
    GTE("大于等于"),

    /**
     * 小于
     */
    LT("小于"),

    /**
     * 小于等于
     */
    LTE("小于等于");

    private final String description;

    ComparisonOperator(String description) {
        this.description = description;
    }

    /** 稳定英文代码。 */
    public String getCode() { return name(); }
    /** 未知码返回 null。 */
    public static ComparisonOperator fromCode(String code) {
        for (ComparisonOperator value : values()) if (value.name().equals(code)) return value;
        return null;
    }
    /** 判断已知代码。 */
    public static boolean isValid(String code) { return fromCode(code) != null; }
    /** 返回独立代码数组。 */
    public static String[] getAllCodes() {
        return java.util.Arrays.stream(values()).map(ComparisonOperator::getCode).toArray(String[]::new);
    }
    /** 默认只输出稳定代码。 */
    @Override
    public String toString() { return getCode(); }
}
