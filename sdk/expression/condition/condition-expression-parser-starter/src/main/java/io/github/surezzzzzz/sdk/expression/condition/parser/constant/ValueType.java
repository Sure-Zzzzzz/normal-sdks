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
}
