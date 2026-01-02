package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.SortOrder;

import java.util.HashMap;
import java.util.Map;

/**
 * 排序关键词库
 *
 * @author surezzzzzz
 */
public class SortKeywords {

    private static final Map<String, SortOrder> KEYWORD_MAP = new HashMap<>();

    static {
        // ASC
        register("升序", SortOrder.ASC);
        register("从小到大", SortOrder.ASC);
        register("正序", SortOrder.ASC);
        register("asc", SortOrder.ASC);
        register("ASC", SortOrder.ASC);

        // DESC
        register("降序", SortOrder.DESC);
        register("从大到小", SortOrder.DESC);
        register("倒序", SortOrder.DESC);
        register("desc", SortOrder.DESC);
        register("DESC", SortOrder.DESC);
    }

    private static void register(String keyword, SortOrder order) {
        KEYWORD_MAP.put(keyword, order);
    }

    /**
     * 根据关键词获取排序顺序
     */
    public static SortOrder fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        // 先尝试精确匹配
        SortOrder result = KEYWORD_MAP.get(keyword);
        if (result != null) {
            return result;
        }
        // 如果是英文，尝试小写匹配
        if (keyword.matches("[a-zA-Z]+")) {
            return KEYWORD_MAP.get(keyword.toLowerCase());
        }
        return null;
    }

    /**
     * 判断是否为排序关键词
     */
    public static boolean isSortKeyword(String keyword) {
        return keyword != null && KEYWORD_MAP.containsKey(keyword);
    }

    /**
     * 获取所有关键词
     */
    public static Map<String, SortOrder> getAllKeywords() {
        return new HashMap<>(KEYWORD_MAP);
    }
}
