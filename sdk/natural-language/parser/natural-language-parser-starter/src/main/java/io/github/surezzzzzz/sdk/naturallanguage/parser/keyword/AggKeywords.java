package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;

import java.util.HashMap;
import java.util.Map;

/**
 * 聚合关键词库
 *
 * @author surezzzzzz
 */
public class AggKeywords {

    private static final Map<String, AggType> KEYWORD_MAP = new HashMap<>();

    static {
        // AVG
        register("平均", AggType.AVG);
        register("平均值", AggType.AVG);
        register("均值", AggType.AVG);
        register("avg", AggType.AVG);
        register("AVG", AggType.AVG);

        // SUM
        register("求和", AggType.SUM);
        register("总和", AggType.SUM);
        register("汇总", AggType.SUM);
        register("sum", AggType.SUM);
        register("SUM", AggType.SUM);

        // MAX
        register("最大", AggType.MAX);
        register("最大值", AggType.MAX);
        register("max", AggType.MAX);
        register("MAX", AggType.MAX);

        // MIN
        register("最小", AggType.MIN);
        register("最小值", AggType.MIN);
        register("min", AggType.MIN);
        register("MIN", AggType.MIN);

        // COUNT
        register("计数", AggType.COUNT);
        register("数量", AggType.COUNT);
        register("个数", AggType.COUNT);
        register("count", AggType.COUNT);
        register("COUNT", AggType.COUNT);

        // CARDINALITY
        register("去重", AggType.CARDINALITY);
        register("去重计数", AggType.CARDINALITY);
        register("唯一值", AggType.CARDINALITY);
        register("distinct", AggType.CARDINALITY);

        // STATS
        register("统计分析", AggType.STATS);
        register("详细统计", AggType.STATS);
        register("stats", AggType.STATS);

        // TERMS
        register("分组", AggType.TERMS);
        register("按...分组", AggType.TERMS);
        register("group", AggType.TERMS);
        register("terms", AggType.TERMS);

        // DATE_HISTOGRAM
        register("按日期", AggType.DATE_HISTOGRAM);
        register("日期分组", AggType.DATE_HISTOGRAM);
        register("时间分组", AggType.DATE_HISTOGRAM);

        // HISTOGRAM
        register("按区间", AggType.HISTOGRAM);
        register("区间分组", AggType.HISTOGRAM);
        register("直方图", AggType.HISTOGRAM);

        // RANGE
        register("范围分组", AggType.RANGE);
        register("range", AggType.RANGE);
    }

    private static void register(String keyword, AggType type) {
        KEYWORD_MAP.put(keyword, type);
    }

    /**
     * 根据关键词获取聚合类型
     */
    public static AggType fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        // 先尝试精确匹配
        AggType result = KEYWORD_MAP.get(keyword);
        if (result != null) {
            return result;
        }
        // 如果是英文，尝试小写匹配
        if (keyword.matches("[a-zA-Z_]+")) {
            return KEYWORD_MAP.get(keyword.toLowerCase());
        }
        return null;
    }

    /**
     * 判断是否为聚合关键词
     */
    public static boolean isAggKeyword(String keyword) {
        return keyword != null && KEYWORD_MAP.containsKey(keyword);
    }

    /**
     * 获取所有关键词
     */
    public static Map<String, AggType> getAllKeywords() {
        return new HashMap<>(KEYWORD_MAP);
    }
}
