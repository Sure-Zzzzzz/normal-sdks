package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

/**
 * 值类型枚举
 * 定义条件表达式中支持的值类型
 *
 * @author surezzzzzz
 */
public enum ValueType {

    /**
     * 字符串类型
     * 示例：'活跃', "用户"
     */
    STRING("字符串"),

    /**
     * 整数类型
     * 示例：123, -456
     */
    INTEGER("整数"),

    /**
     * 浮点数类型
     * 示例：123.45, -67.89
     */
    DECIMAL("浮点数"),

    /**
     * 布尔类型
     * 示例：true, false, '是', '否'
     */
    BOOLEAN("布尔值"),

    /**
     * 时间范围快捷表达式类型
     * 示例：'近1个月', '近三个月', '近半年'
     */
    TIME_RANGE("时间范围"),

    /**
     * 空值类型
     * 用于 IS NULL / IS NOT NULL
     */
    NULL("空值");

    private final String description;

    ValueType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
