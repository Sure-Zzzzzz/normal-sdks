package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 自然语言解析器关键词常量
 *
 * @author surezzzzzz
 */
public class NLParserKeywords {

    // ========== 索引/表名提示词 ==========

    /**
     * 索引/表名标识关键词（中文）
     */
    public static final Set<String> INDEX_INDICATORS_CN = new HashSet<>(Arrays.asList(
            "索引",
            "表"
    ));

    /**
     * 索引/表名标识关键词（英文）
     */
    public static final Set<String> INDEX_INDICATORS_EN = new HashSet<>(Arrays.asList(
            "index",
            "table",
            "collection"
    ));

    /**
     * 指示词（用于跳过）
     */
    public static final Set<String> DEMONSTRATIVE_WORDS = new HashSet<>(Arrays.asList(
            "这个",
            "那个",
            "这",
            "那"
    ));

    // ========== 排序相关关键词 ==========

    /**
     * 排序字段边界关键词
     */
    public static final String SORT_BY = "按";

    // ========== 分页相关关键词 ==========

    /**
     * 分页limit关键词（中文）
     */
    public static final Set<String> LIMIT_KEYWORDS_CN = new HashSet<>(Arrays.asList(
            "限制",
            "最多",
            "前",
            "返回",
            "取"    // 取N条
    ));

    /**
     * 分页limit关键词（英文）
     */
    public static final Set<String> LIMIT_KEYWORDS_EN = new HashSet<>(Arrays.asList(
            "limit"
    ));

    /**
     * 分页offset关键词（中文）
     */
    public static final Set<String> OFFSET_KEYWORDS_CN = new HashSet<>(Arrays.asList(
            "跳过",
            "跳过前",
            "忽略",
            "忽略前"
    ));

    /**
     * 分页offset关键词（英文）
     */
    public static final Set<String> OFFSET_KEYWORDS_EN = new HashSet<>(Arrays.asList(
            "skip",
            "offset"
    ));

    /**
     * 分页page关键词（中文）
     */
    public static final Set<String> PAGE_KEYWORDS_CN = new HashSet<>(Arrays.asList(
            "第",
            "页"
    ));

    /**
     * 分页page关键词（英文）
     */
    public static final Set<String> PAGE_KEYWORDS_EN = new HashSet<>(Arrays.asList(
            "page"
    ));

    /**
     * 每页size关键词
     */
    public static final Set<String> SIZE_KEYWORDS = new HashSet<>(Arrays.asList(
            "每页",
            "每",  // "每页"可能被分词为"每"+"页"
            "size"
    ));

    /**
     * 起始位置关键词
     */
    public static final Set<String> FROM_KEYWORDS = new HashSet<>(Arrays.asList(
            "从",
            "从第",
            "起始",
            "from"
    ));

    /**
     * 范围连接词
     */
    public static final Set<String> RANGE_KEYWORDS = new HashSet<>(Arrays.asList(
            "到",
            "至",
            "到第",
            "至第",
            "~",
            "-"
    ));

    /**
     * 续查关键词（用于search_after）
     */
    public static final Set<String> CONTINUE_KEYWORDS = new HashSet<>(Arrays.asList(
            "继续",
            "继续查询",
            "接着",
            "接着查",
            "下一页",
            "下一",  // "下一页"可能被分词为"下一"+"页"
            "continue"
    ));

    // ========== 辅助方法 ==========

    /**
     * 判断是否为索引指示关键词
     */
    public static boolean isIndexIndicator(String word) {
        if (word == null) {
            return false;
        }
        return INDEX_INDICATORS_CN.contains(word) ||
                INDEX_INDICATORS_EN.stream().anyMatch(keyword -> keyword.equalsIgnoreCase(word));
    }

    /**
     * 判断是否为指示词
     */
    public static boolean isDemonstrativeWord(String word) {
        return word != null && DEMONSTRATIVE_WORDS.contains(word);
    }

    /**
     * 判断是否为排序边界关键词
     */
    public static boolean isSortBoundary(String word) {
        return SORT_BY.equals(word);
    }

    /**
     * 判断是否为limit关键词
     */
    public static boolean isLimitKeyword(String word) {
        if (word == null) {
            return false;
        }
        return LIMIT_KEYWORDS_CN.contains(word) ||
                LIMIT_KEYWORDS_EN.stream().anyMatch(keyword -> keyword.equalsIgnoreCase(word));
    }

    /**
     * 判断是否为offset关键词
     */
    public static boolean isOffsetKeyword(String word) {
        if (word == null) {
            return false;
        }
        return OFFSET_KEYWORDS_CN.contains(word) ||
                OFFSET_KEYWORDS_EN.stream().anyMatch(keyword -> keyword.equalsIgnoreCase(word));
    }

    /**
     * 判断是否为page关键词
     */
    public static boolean isPageKeyword(String word) {
        if (word == null) {
            return false;
        }
        return PAGE_KEYWORDS_CN.contains(word) ||
                PAGE_KEYWORDS_EN.stream().anyMatch(keyword -> keyword.equalsIgnoreCase(word));
    }

    /**
     * 判断是否为size关键词
     */
    public static boolean isSizeKeyword(String word) {
        return word != null && SIZE_KEYWORDS.stream().anyMatch(keyword -> keyword.equalsIgnoreCase(word));
    }

    /**
     * 判断是否为from关键词（起始位置）
     */
    public static boolean isFromKeyword(String word) {
        return word != null && FROM_KEYWORDS.stream().anyMatch(keyword -> keyword.equalsIgnoreCase(word));
    }

    /**
     * 判断是否为范围连接词
     */
    public static boolean isRangeKeyword(String word) {
        return word != null && RANGE_KEYWORDS.contains(word);
    }

    /**
     * 判断是否为续查关键词
     */
    public static boolean isContinueKeyword(String word) {
        return word != null && CONTINUE_KEYWORDS.stream().anyMatch(keyword -> keyword.equalsIgnoreCase(word));
    }

    private NLParserKeywords() {
        // 私有构造函数，防止实例化
    }
}
