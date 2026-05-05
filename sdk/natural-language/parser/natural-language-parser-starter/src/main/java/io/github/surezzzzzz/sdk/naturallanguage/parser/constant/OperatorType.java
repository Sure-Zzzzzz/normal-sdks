package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

import lombok.Getter;

/**
 * 操作符类型枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum OperatorType {

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
    BETWEEN("between", "在范围内"),

    /**
     * 模糊匹配
     */
    LIKE("like", "模糊匹配"),

    /**
     * 模糊不匹配
     */
    NOT_LIKE("not_like", "模糊不匹配"),

    /**
     * 前缀匹配
     */
    PREFIX("prefix", "前缀匹配"),

    /**
     * 后缀匹配
     */
    SUFFIX("suffix", "后缀匹配"),

    /**
     * 正则表达式匹配
     */
    REGEX("regex", "正则匹配"),

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
    IS_NOT_NULL("is_not_null", "不为空");

    private final String code;
    private final String description;

    OperatorType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static OperatorType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OperatorType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
     *
     * @param code 类型代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        OperatorType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    /**
     * 是否需要值
     *
     * @return true 需要，false 不需要
     */
    public boolean needsValue() {
        return this != EXISTS && this != NOT_EXISTS && this != IS_NULL && this != IS_NOT_NULL;
    }

    /**
     * 是否需要多个值
     *
     * @return true 需要，false 不需要
     */
    public boolean needsMultipleValues() {
        return this == IN || this == NOT_IN || this == BETWEEN;
    }

    @Override
    public String toString() {
        return code;
    }
}
