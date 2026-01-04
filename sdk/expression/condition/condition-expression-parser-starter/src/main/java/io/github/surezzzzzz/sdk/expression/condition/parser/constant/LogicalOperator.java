package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

/**
 * 逻辑运算符枚举
 * 用于连接多个条件表达式
 *
 * @author surezzzzzz
 */
public enum LogicalOperator {

    /**
     * 逻辑与（所有条件同时满足）
     */
    AND("AND", "且"),

    /**
     * 逻辑或（任一条件满足即可）
     */
    OR("OR", "或");

    private final String code;
    private final String description;

    LogicalOperator(String code, String description) {
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
