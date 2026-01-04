package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

/**
 * 匹配运算符枚举
 * 用于模糊匹配、前缀匹配、后缀匹配
 *
 * @author surezzzzzz
 */
public enum MatchOperator {

    /**
     * 模糊匹配（包含）
     * 示例：名称 LIKE '用户' -> 匹配包含"用户"的所有值
     */
    LIKE("LIKE", "模糊匹配"),

    /**
     * 前缀匹配
     * 示例：名称 PREFIX LIKE '用户' -> 匹配以"用户"开头的值
     */
    PREFIX("PREFIX", "前缀匹配"),

    /**
     * 后缀匹配
     * 示例：名称 SUFFIX LIKE '.com' -> 匹配以".com"结尾的值
     */
    SUFFIX("SUFFIX", "后缀匹配"),

    /**
     * 不匹配（排除模糊匹配）
     * 示例：名称 NOT LIKE '测试' -> 排除包含"测试"的所有值
     */
    NOT_LIKE("NOT_LIKE", "不匹配");

    private final String code;
    private final String description;

    MatchOperator(String code, String description) {
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
