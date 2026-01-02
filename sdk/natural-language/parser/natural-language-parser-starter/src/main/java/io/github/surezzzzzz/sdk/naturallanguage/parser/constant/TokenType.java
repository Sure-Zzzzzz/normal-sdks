package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

/**
 * Token 类型
 *
 * @author surezzzzzz
 */
public enum TokenType {

    /**
     * 操作符
     */
    OPERATOR,

    /**
     * 逻辑运算符
     */
    LOGIC,

    /**
     * 聚合关键词
     */
    AGGREGATION,

    /**
     * 排序关键词
     */
    SORT,

    /**
     * 数值
     */
    NUMBER,

    /**
     * 字段候选（可能是字段名）
     */
    FIELD_CANDIDATE,

    /**
     * 值
     */
    VALUE,

    /**
     * 分隔符（逗号、顿号等）
     */
    DELIMITER,

    /**
     * 停用词（可忽略）
     */
    STOP_WORD,

    /**
     * 未知
     */
    UNKNOWN
}
