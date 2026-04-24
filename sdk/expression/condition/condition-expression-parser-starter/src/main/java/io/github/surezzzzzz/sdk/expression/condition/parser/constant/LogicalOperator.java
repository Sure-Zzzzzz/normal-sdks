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
}
