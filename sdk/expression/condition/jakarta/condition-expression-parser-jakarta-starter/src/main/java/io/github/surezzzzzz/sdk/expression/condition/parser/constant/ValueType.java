package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

import lombok.Getter;

/**
 * 值类型枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum ValueType {

    /**
     * 字符串类型
     */
    STRING("字符串"),

    /**
     * 整数类型
     */
    INTEGER("整数"),

    /**
     * 浮点数类型
     */
    DECIMAL("浮点数"),

    /**
     * 布尔类型
     */
    BOOLEAN("布尔值"),

    /**
     * 时间范围快捷表达式类型
     */
    TIME_RANGE("时间范围"),

    /**
     * 空值类型
     */
    NULL("空值");

    private final String description;

    ValueType(String description) {
        this.description = description;
    }

    /** 稳定英文代码。 */
    public String getCode() { return name(); }
    /** 未知码返回 null。 */
    public static ValueType fromCode(String code) {
        for (ValueType value : values()) if (value.name().equals(code)) return value;
        return null;
    }
    /** 判断已知代码。 */
    public static boolean isValid(String code) { return fromCode(code) != null; }
    /** 返回独立代码数组。 */
    public static String[] getAllCodes() {
        return java.util.Arrays.stream(values()).map(ValueType::getCode).toArray(String[]::new);
    }
    /** 默认只输出稳定代码。 */
    @Override
    public String toString() { return getCode(); }
}
