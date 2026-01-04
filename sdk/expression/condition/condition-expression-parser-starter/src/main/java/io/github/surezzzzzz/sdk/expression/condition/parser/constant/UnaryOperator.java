package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

/**
 * 一元运算符枚举
 * 用于单个表达式的逻辑取反
 *
 * @author surezzzzzz
 */
public enum UnaryOperator {

    /**
     * 逻辑非（取反）
     * 示例：NOT (类型='活跃') -> 取反整个表达式
     */
    NOT("NOT", "逻辑非");

    private final String code;
    private final String description;

    UnaryOperator(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
