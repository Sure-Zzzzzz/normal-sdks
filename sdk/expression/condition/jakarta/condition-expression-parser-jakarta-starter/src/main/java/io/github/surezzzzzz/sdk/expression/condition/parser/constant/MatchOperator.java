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
    NOT_SUFFIX("后缀不匹配"),

    /**
     * 字段存在
     */
    EXISTS("字段存在"),

    /**
     * 字段不存在
     */
    NOT_EXISTS("字段不存在");

    private final String description;

    MatchOperator(String description) {
        this.description = description;
    }

    /** 稳定英文代码。 */
    public String getCode() { return name(); }
    /** 未知码返回 null。 */
    public static MatchOperator fromCode(String code) {
        for (MatchOperator value : values()) if (value.name().equals(code)) return value;
        return null;
    }
    /** 判断已知代码。 */
    public static boolean isValid(String code) { return fromCode(code) != null; }
    /** 返回独立代码数组。 */
    public static String[] getAllCodes() {
        return java.util.Arrays.stream(values()).map(MatchOperator::getCode).toArray(String[]::new);
    }
    /** 默认只输出稳定代码。 */
    @Override
    public String toString() { return getCode(); }
}
