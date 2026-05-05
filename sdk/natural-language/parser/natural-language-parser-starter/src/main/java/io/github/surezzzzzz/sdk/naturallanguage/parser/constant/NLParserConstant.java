package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

/**
 * Natural Language Parser Constants
 *
 * @author surezzzzzz
 */
public final class NLParserConstant {

    private NLParserConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX =
            "io.github.surezzzzzz.sdk.natural-language.parser";

    // ==================== 解析器参数 ====================

    /**
     * 字段向前查找最大距离
     */
    public static final int MAX_FIELD_LOOKAHEAD_DISTANCE = 5;

    /**
     * 字段向后查找最大距离
     */
    public static final int MAX_FIELD_LOOKBEHIND_DISTANCE = 5;

    /**
     * 排序模式检测向前查找距离
     */
    public static final int SORT_PATTERN_LOOKAHEAD = 4;
}
