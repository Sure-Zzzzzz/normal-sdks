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
}
