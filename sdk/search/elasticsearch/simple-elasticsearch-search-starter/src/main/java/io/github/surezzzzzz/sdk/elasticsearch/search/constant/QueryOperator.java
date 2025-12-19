package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 查询操作符枚举
 *
 * @author surezzzzzz
 */
public enum QueryOperator {

    /**
     * 等于
     */
    EQ("eq", "等于"),

    /**
     * 不等于
     */
    NE("ne", "不等于"),

    /**
     * 大于
     */
    GT("gt", "大于"),

    /**
     * 大于等于
     */
    GTE("gte", "大于等于"),

    /**
     * 小于
     */
    LT("lt", "小于"),

    /**
     * 小于等于
     */
    LTE("lte", "小于等于"),

    /**
     * 在列表中
     */
    IN("in", "在列表中"),

    /**
     * 不在列表中
     */
    NOT_IN("not_in", "不在列表中"),

    /**
     * 范围查询（需要两个值: from 和 to）
     */
    BETWEEN("between", "范围查询"),

    /**
     * 模糊匹配（根据字段类型自动选择 match 或 wildcard）
     */
    LIKE("like", "模糊匹配"),

    /**
     * 前缀匹配
     */
    PREFIX("prefix", "前缀匹配"),

    /**
     * 后缀匹配（转换为 wildcard: *xxx）
     */
    SUFFIX("suffix", "后缀匹配"),

    /**
     * 字段存在
     */
    EXISTS("exists", "字段存在"),

    /**
     * 字段不存在
     */
    NOT_EXISTS("not_exists", "字段不存在"),

    /**
     * 字段为 null
     */
    IS_NULL("is_null", "为空"),

    /**
     * 字段不为 null
     */
    IS_NOT_NULL("is_not_null", "不为空"),

    /**
     * 正则表达式匹配
     */
    REGEX("regex", "正则匹配");

    private final String operator;
    private final String description;

    QueryOperator(String operator, String description) {
        this.operator = operator;
        this.description = description;
    }

    public String getOperator() {
        return operator;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据字符串获取枚举值
     */
    public static QueryOperator fromString(String operator) {
        if (operator == null) {
            return null;
        }
        for (QueryOperator op : values()) {
            if (op.operator.equalsIgnoreCase(operator)) {
                return op;
            }
        }
        throw new IllegalArgumentException("不支持的操作符: " + operator);
    }

    /**
     * 是否需要值
     */
    public boolean needsValue() {
        return this != EXISTS && this != NOT_EXISTS && this != IS_NULL && this != IS_NOT_NULL;
    }

    /**
     * 是否需要多个值
     */
    public boolean needsMultipleValues() {
        return this == IN || this == NOT_IN || this == BETWEEN;
    }
}
