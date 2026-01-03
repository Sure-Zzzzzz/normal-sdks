package io.github.surezzzzzz.sdk.naturallanguage.parser.keyword;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聚合关键词库
 *
 * @author surezzzzzz
 */
public class AggKeywords {

    private static final Map<String, AggType> KEYWORD_MAP = new HashMap<>();

    // 桶聚合前缀词（"按"、"每"）
    private static final Set<String> BUCKET_PREFIXES = new HashSet<>();

    // TERMS聚合前缀词（"按"、"by"）
    private static final Set<String> TERMS_PREFIXES = new HashSet<>();

    // DATE_HISTOGRAM聚合前缀词（"每"）
    private static final Set<String> DATE_HISTOGRAM_PREFIXES = new HashSet<>();

    // 聚合分隔词（"同时"、"并且"）
    private static final Set<String> AGG_SEPARATORS = new HashSet<>();

    // 嵌套指示词（"统计"、"计算"）
    private static final Set<String> NESTED_INDICATORS = new HashSet<>();

    // 时间间隔映射
    private static final Map<String, String> INTERVAL_MAP = new HashMap<>();

    // size参数模式：前N个、限制N个
    private static final Pattern SIZE_PATTERN = Pattern.compile("(前|限制|最多|取前|top)\\s*(\\d+)\\s*(个|条)?");

    static {
        // ========== 指标聚合 ==========
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

        // ========== 桶聚合 ==========
        // TERMS（分组）
        register("分组", AggType.TERMS);
        register("group", AggType.TERMS);
        register("terms", AggType.TERMS);
        register("group by", AggType.TERMS);

        // DATE_HISTOGRAM（时间聚合）
        register("日期分组", AggType.DATE_HISTOGRAM);
        register("时间分组", AggType.DATE_HISTOGRAM);
        register("date_histogram", AggType.DATE_HISTOGRAM);

        // HISTOGRAM
        register("区间分组", AggType.HISTOGRAM);
        register("直方图", AggType.HISTOGRAM);
        register("histogram", AggType.HISTOGRAM);

        // RANGE
        register("范围分组", AggType.RANGE);
        register("range", AggType.RANGE);

        // ========== 桶聚合前缀词 ==========
        BUCKET_PREFIXES.add("按");
        BUCKET_PREFIXES.add("每");
        BUCKET_PREFIXES.add("by");

        // TERMS聚合前缀词（"按"、"by"）
        TERMS_PREFIXES.add("按");
        TERMS_PREFIXES.add("by");

        // DATE_HISTOGRAM聚合前缀词（"每"）
        DATE_HISTOGRAM_PREFIXES.add("每");

        // ========== 聚合分隔词 ==========
        AGG_SEPARATORS.add("同时");
        AGG_SEPARATORS.add("并且");
        AGG_SEPARATORS.add("还有");
        AGG_SEPARATORS.add("以及");
        AGG_SEPARATORS.add("和");
        AGG_SEPARATORS.add("另外");
        AGG_SEPARATORS.add("再");
        AGG_SEPARATORS.add("且");
        AGG_SEPARATORS.add("及");
        AGG_SEPARATORS.add(",");    // 逗号也可以作为聚合分隔符
        AGG_SEPARATORS.add("，");   // 中文逗号

        // ========== 嵌套指示词 ==========
        NESTED_INDICATORS.add("统计");
        NESTED_INDICATORS.add("计算");
        NESTED_INDICATORS.add("求");
        NESTED_INDICATORS.add("每组");
        NESTED_INDICATORS.add("各个");
        NESTED_INDICATORS.add("各");
        NESTED_INDICATORS.add("的");

        // ========== 时间间隔映射 ==========
        // 中文时间单位
        INTERVAL_MAP.put("每天", "1d");
        INTERVAL_MAP.put("每日", "1d");
        INTERVAL_MAP.put("按天", "1d");
        INTERVAL_MAP.put("天", "1d");

        INTERVAL_MAP.put("每小时", "1h");
        INTERVAL_MAP.put("每时", "1h");
        INTERVAL_MAP.put("按小时", "1h");
        INTERVAL_MAP.put("小时", "1h");

        INTERVAL_MAP.put("每周", "1w");
        INTERVAL_MAP.put("周", "1w");

        INTERVAL_MAP.put("每月", "1M");
        INTERVAL_MAP.put("月", "1M");

        INTERVAL_MAP.put("每年", "1y");
        INTERVAL_MAP.put("年", "1y");

        // ES格式
        INTERVAL_MAP.put("1d", "1d");
        INTERVAL_MAP.put("1h", "1h");
        INTERVAL_MAP.put("1w", "1w");
        INTERVAL_MAP.put("1M", "1M");
        INTERVAL_MAP.put("1y", "1y");
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
        if (keyword.matches("[a-zA-Z_\\s]+")) {
            return KEYWORD_MAP.get(keyword.toLowerCase());
        }
        return null;
    }

    /**
     * 判断是否为聚合关键词
     */
    public static boolean isAggKeyword(String keyword) {
        return fromKeyword(keyword) != null;
    }

    /**
     * 判断是否为桶聚合前缀词（"按"、"每"）
     */
    public static boolean isBucketPrefix(String keyword) {
        return keyword != null && BUCKET_PREFIXES.contains(keyword);
    }

    /**
     * 判断是否为TERMS聚合前缀词（"按"、"by"）
     */
    public static boolean isTermsPrefix(String keyword) {
        return keyword != null && TERMS_PREFIXES.contains(keyword);
    }

    /**
     * 判断是否为DATE_HISTOGRAM聚合前缀词（"每"）
     */
    public static boolean isDateHistogramPrefix(String keyword) {
        return keyword != null && DATE_HISTOGRAM_PREFIXES.contains(keyword);
    }

    /**
     * 判断是否为聚合分隔词（"同时"、"并且"）
     */
    public static boolean isAggSeparator(String keyword) {
        return keyword != null && AGG_SEPARATORS.contains(keyword);
    }

    /**
     * 判断是否为嵌套指示词（"统计"、"计算"）
     */
    public static boolean isNestedIndicator(String keyword) {
        return keyword != null && NESTED_INDICATORS.contains(keyword);
    }

    /**
     * 从文本中提取size参数
     * 支持格式：前10个、限制5条、最多20个、top 10
     *
     * @param text 文本
     * @return size值，如果没找到则返回null
     */
    public static Integer extractSize(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = SIZE_PATTERN.matcher(text);
        if (matcher.find()) {
            String numberStr = matcher.group(2);
            try {
                return Integer.parseInt(numberStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * 获取时间间隔（interval）
     *
     * @param keyword 关键词（如："每天"、"1d"）
     * @return interval字符串，如果不是时间间隔则返回null
     */
    public static String getInterval(String keyword) {
        if (keyword == null) {
            return null;
        }
        return INTERVAL_MAP.get(keyword);
    }

    /**
     * 判断是否为时间间隔关键词
     */
    public static boolean isIntervalKeyword(String keyword) {
        return getInterval(keyword) != null;
    }

    /**
     * 获取所有关键词
     */
    public static Map<String, AggType> getAllKeywords() {
        return new HashMap<>(KEYWORD_MAP);
    }
}
