package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

import lombok.Getter;

/**
 * 匹配运算符枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum MatchOperator {

    /**
     * 模糊匹配（包含）
     */
    LIKE("模糊匹配"),

    /**
     * 前缀匹配
     */
    PREFIX("前缀匹配"),

    /**
     * 后缀匹配
     */
    SUFFIX("后缀匹配"),

    /**
     * 不匹配（排除模糊匹配）
     */
    NOT_LIKE("不匹配"),

    /**
     * 前缀不匹配
     */
    NOT_PREFIX("前缀不匹配"),

    /**
     * 后缀不匹配
     */
    NOT_SUFFIX("后缀不匹配");

    private final String description;

    MatchOperator(String description) {
        this.description = description;
    }
}
