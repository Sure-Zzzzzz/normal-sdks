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
}
