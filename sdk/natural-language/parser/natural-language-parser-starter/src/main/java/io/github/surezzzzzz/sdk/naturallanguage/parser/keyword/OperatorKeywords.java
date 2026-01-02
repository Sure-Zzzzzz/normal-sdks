package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.OperatorType;

import java.util.HashMap;
import java.util.Map;

/**
 * 操作符关键词库
 *
 * @author surezzzzzz
 */
public class OperatorKeywords {

    private static final Map<String, OperatorType> KEYWORD_MAP = new HashMap<>();

    static {
        // 等于
        register("等于", OperatorType.EQ);
        register("是", OperatorType.EQ);
        register("为", OperatorType.EQ);
        register("==", OperatorType.EQ);
        register("=", OperatorType.EQ);

        // 不等于
        register("不等于", OperatorType.NE);
        register("不是", OperatorType.NE);
        register("不为", OperatorType.NE);
        register("!=", OperatorType.NE);

        // 大于
        register("大于", OperatorType.GT);
        register(">", OperatorType.GT);
        register("超过", OperatorType.GT);
        register("多于", OperatorType.GT);

        // 大于等于
        register("大于等于", OperatorType.GTE);
        register(">=", OperatorType.GTE);
        register("不小于", OperatorType.GTE);
        register("至少", OperatorType.GTE);

        // 小于
        register("小于", OperatorType.LT);
        register("<", OperatorType.LT);
        register("低于", OperatorType.LT);
        register("少于", OperatorType.LT);

        // 小于等于
        register("小于等于", OperatorType.LTE);
        register("<=", OperatorType.LTE);
        register("不大于", OperatorType.LTE);
        register("最多", OperatorType.LTE);

        // IN
        register("在", OperatorType.IN);
        register("属于", OperatorType.IN);
        register("包括", OperatorType.IN);
        register("in", OperatorType.IN);
        register("IN", OperatorType.IN);

        // NOT IN
        register("不在", OperatorType.NOT_IN);
        register("不属于", OperatorType.NOT_IN);
        register("不包括", OperatorType.NOT_IN);

        // LIKE
        register("包含", OperatorType.LIKE);
        register("含有", OperatorType.LIKE);
        register("匹配", OperatorType.LIKE);
        register("like", OperatorType.LIKE);
        register("LIKE", OperatorType.LIKE);

        // PREFIX
        register("开头是", OperatorType.PREFIX);
        register("以...开头", OperatorType.PREFIX);
        register("前缀", OperatorType.PREFIX);

        // SUFFIX
        register("结尾是", OperatorType.SUFFIX);
        register("以...结尾", OperatorType.SUFFIX);
        register("后缀", OperatorType.SUFFIX);

        // BETWEEN
        register("在...之间", OperatorType.BETWEEN);
        register("介于", OperatorType.BETWEEN);
        register("范围", OperatorType.BETWEEN);
        register("between", OperatorType.BETWEEN);
        register("BETWEEN", OperatorType.BETWEEN);

        // EXISTS
        register("存在", OperatorType.EXISTS);
        register("有", OperatorType.EXISTS);

        // NOT EXISTS
        register("不存在", OperatorType.NOT_EXISTS);
        register("没有", OperatorType.NOT_EXISTS);

        // IS NULL
        register("为空", OperatorType.IS_NULL);
        register("是空", OperatorType.IS_NULL);
        register("null", OperatorType.IS_NULL);

        // IS NOT NULL
        register("不为空", OperatorType.IS_NOT_NULL);
        register("不是空", OperatorType.IS_NOT_NULL);
        register("非空", OperatorType.IS_NOT_NULL);

        // REGEX
        register("正则", OperatorType.REGEX);
        register("正则匹配", OperatorType.REGEX);
        register("regex", OperatorType.REGEX);
    }

    private static void register(String keyword, OperatorType type) {
        KEYWORD_MAP.put(keyword, type);
    }

    /**
     * 根据关键词获取操作符类型
     */
    public static OperatorType fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        // 先尝试精确匹配
        OperatorType result = KEYWORD_MAP.get(keyword);
        if (result != null) {
            return result;
        }
        // 如果是英文或符号，尝试小写匹配
        if (keyword.matches("[a-zA-Z_]+")) {
            return KEYWORD_MAP.get(keyword.toLowerCase());
        }
        return null;
    }

    /**
     * 判断是否为操作符关键词
     */
    public static boolean isOperatorKeyword(String keyword) {
        return keyword != null && KEYWORD_MAP.containsKey(keyword);
    }

    /**
     * 获取所有关键词
     */
    public static Map<String, OperatorType> getAllKeywords() {
        return new HashMap<>(KEYWORD_MAP);
    }
}
