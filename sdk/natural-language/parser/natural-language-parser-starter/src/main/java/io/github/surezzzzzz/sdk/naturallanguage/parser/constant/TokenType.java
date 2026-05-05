package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

import lombok.Getter;

/**
 * Token 类型枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum TokenType {

    /**
     * 操作符
     */
    OPERATOR("operator", "操作符"),

    /**
     * 逻辑运算符
     */
    LOGIC("logic", "逻辑运算符"),

    /**
     * 聚合关键词
     */
    AGGREGATION("aggregation", "聚合关键词"),

    /**
     * 排序关键词
     */
    SORT("sort", "排序关键词"),

    /**
     * 数值
     */
    NUMBER("number", "数值"),

    /**
     * 字段候选（可能是字段名）
     */
    FIELD_CANDIDATE("field_candidate", "字段候选"),

    /**
     * 值
     */
    VALUE("value", "值"),

    /**
     * 分隔符（逗号、顿号等）
     */
    DELIMITER("delimiter", "分隔符"),

    /**
     * 停用词（可忽略）
     */
    STOP_WORD("stop_word", "停用词"),

    /**
     * 折叠关键词
     */
    COLLAPSE("collapse", "折叠关键词"),

    /**
     * 时间范围关键词
     */
    TIME_RANGE("time_range", "时间范围关键词"),

    /**
     * 分页关键词
     */
    PAGINATION("pagination", "分页关键词"),

    /**
     * 介词（跳过）
     */
    PREPOSITION("preposition", "介词"),

    /**
     * 未知
     */
    UNKNOWN("unknown", "未知");

    private final String code;
    private final String description;

    TokenType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static TokenType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TokenType type : values()) {
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
        TokenType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
