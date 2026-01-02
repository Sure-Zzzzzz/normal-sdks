package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.LogicType;

import java.util.HashMap;
import java.util.Map;

/**
 * 逻辑关键词库
 *
 * @author surezzzzzz
 */
public class LogicKeywords {

    private static final Map<String, LogicType> KEYWORD_MAP = new HashMap<>();

    static {
        // AND
        register("并且", LogicType.AND);
        register("且", LogicType.AND);
        register("和", LogicType.AND);
        register("同时", LogicType.AND);
        register("并", LogicType.AND);
        register("而且", LogicType.AND);
        register("and", LogicType.AND);
        register("AND", LogicType.AND);
        register("&&", LogicType.AND);

        // OR
        register("或者", LogicType.OR);
        register("或", LogicType.OR);
        register("要么", LogicType.OR);
        register("或是", LogicType.OR);
        register("or", LogicType.OR);
        register("OR", LogicType.OR);
        register("||", LogicType.OR);
    }

    private static void register(String keyword, LogicType type) {
        KEYWORD_MAP.put(keyword, type);
    }

    /**
     * 根据关键词获取逻辑类型
     */
    public static LogicType fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        // 先尝试精确匹配
        LogicType result = KEYWORD_MAP.get(keyword);
        if (result != null) {
            return result;
        }
        // 如果是英文，尝试小写匹配
        if (keyword.matches("[a-zA-Z&|]+")) {
            return KEYWORD_MAP.get(keyword.toLowerCase());
        }
        return null;
    }

    /**
     * 判断是否为逻辑关键词
     */
    public static boolean isLogicKeyword(String keyword) {
        return keyword != null && KEYWORD_MAP.containsKey(keyword);
    }

    /**
     * 获取所有关键词
     */
    public static Map<String, LogicType> getAllKeywords() {
        return new HashMap<>(KEYWORD_MAP);
    }
}
