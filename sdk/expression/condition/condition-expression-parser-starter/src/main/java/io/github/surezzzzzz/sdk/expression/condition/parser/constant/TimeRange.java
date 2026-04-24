package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

import lombok.Getter;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 时间范围枚举
 * 预定义常用的时间范围快捷表达式
 * <p>
 * SDK 只负责识别关键字并返回对应的枚举值，
 * 具体如何计算时间由业务层决定（可使用 amount 和 unit 元数据）
 *
 * @author surezzzzzz
 */
@Getter
public enum TimeRange {

    // ========== 分钟级 ==========
    LAST_5_MINUTES("近5分钟", 5, ChronoUnit.MINUTES, "最近5分钟", "近五分钟", "最近五分钟"),
    LAST_10_MINUTES("近10分钟", 10, ChronoUnit.MINUTES, "最近10分钟", "近十分钟", "最近十分钟"),
    LAST_15_MINUTES("近15分钟", 15, ChronoUnit.MINUTES, "最近15分钟", "近十五分钟", "最近十五分钟"),
    LAST_30_MINUTES("近30分钟", 30, ChronoUnit.MINUTES, "最近30分钟", "近三十分钟", "最近三十分钟"),

    // ========== 小时级 ==========
    LAST_1_HOUR("近1小时", 1, ChronoUnit.HOURS, "最近1小时", "近一小时", "最近一小时"),
    LAST_6_HOURS("近6小时", 6, ChronoUnit.HOURS, "最近6小时", "近六小时", "最近六小时"),
    LAST_12_HOURS("近12小时", 12, ChronoUnit.HOURS, "最近12小时", "近十二小时", "最近十二小时"),
    LAST_24_HOURS("近24小时", 24, ChronoUnit.HOURS, "最近24小时", "近二十四小时", "最近二十四小时"),

    // ========== 天级 ==========
    LAST_1_DAY("近1天", 1, ChronoUnit.DAYS, "最近1天", "近一天", "最近一天"),
    LAST_3_DAYS("近3天", 3, ChronoUnit.DAYS, "最近3天", "近三天", "最近三天"),
    LAST_7_DAYS("近7天", 7, ChronoUnit.DAYS, "最近7天", "近七天", "最近七天"),

    // ========== 周级 ==========
    LAST_1_WEEK("近1周", 1, ChronoUnit.WEEKS, "最近1周", "近一周", "最近一周"),
    LAST_2_WEEKS("近2周", 2, ChronoUnit.WEEKS, "最近2周", "近两周", "最近两周", "近二周", "最近二周"),

    // ========== 月级 ==========
    LAST_1_MONTH("近1个月", 1, ChronoUnit.MONTHS, "最近1个月", "近一个月", "最近一个月", "一个月", "最近14天", "最近十四天", "最近30天", "最近三十天"),
    LAST_2_MONTHS("近2个月", 2, ChronoUnit.MONTHS, "最近2个月", "近两个月", "最近两个月", "最近60天", "最近六十天"),
    LAST_3_MONTHS("近3个月", 3, ChronoUnit.MONTHS, "近三个月", "最近3个月", "最近三个月", "三个月", "最近90天", "最近九十天"),
    LAST_6_MONTHS("近半年", 6, ChronoUnit.MONTHS, "最近6个月", "最近六个月", "半年"),

    // ========== 年级 ==========
    LAST_1_YEAR("近1年", 1, ChronoUnit.YEARS, "最近1年", "近一年", "最近一年", "一年"),
    LAST_2_YEARS("近2年", 2, ChronoUnit.YEARS, "最近2年", "近两年", "最近两年"),
    LAST_3_YEARS("近3年", 3, ChronoUnit.YEARS, "最近3年", "近三年", "最近三年"),

    // ========== 相对时间点 ==========
    TODAY("今天", 0, ChronoUnit.DAYS),
    YESTERDAY("昨天", -1, ChronoUnit.DAYS),
    DAY_BEFORE_YESTERDAY("前天", -2, ChronoUnit.DAYS),

    THIS_WEEK("本周", 0, ChronoUnit.WEEKS),
    LAST_WEEK("上周", -1, ChronoUnit.WEEKS),

    THIS_MONTH("本月", 0, ChronoUnit.MONTHS),
    PREVIOUS_MONTH("上月", -1, ChronoUnit.MONTHS),

    THIS_QUARTER("本季度", 0, ChronoUnit.MONTHS),
    LAST_QUARTER("上季度", -3, ChronoUnit.MONTHS),

    THIS_YEAR("今年", 0, ChronoUnit.YEARS),
    LAST_YEAR("去年", -1, ChronoUnit.YEARS);

    /**
     * 全局关键字查找表（延迟初始化）
     */
    private static volatile Map<String, TimeRange> KEYWORD_MAP;
    private static volatile Map<String, TimeRange> READ_ONLY_MAP;

    /**
     * 主关键字
     */
    private final String keyword;

    /**
     * 别名列表
     */
    private final String[] aliases;

    /**
     * 数量（可为负数表示过去）
     */
    private final int amount;

    /**
     * 时间单位
     */
    private final ChronoUnit unit;

    TimeRange(String keyword, int amount, ChronoUnit unit, String... aliases) {
        this.keyword = keyword;
        this.aliases = aliases;
        this.amount = amount;
        this.unit = unit;
    }

    /**
     * 是否为"近X时间"类型（向前推算）
     */
    public boolean isLastType() {
        return keyword.startsWith("近");
    }

    /**
     * 是否为"今/本/上"类型（相对时间点）
     */
    public boolean isRelativeType() {
        return keyword.startsWith("今") || keyword.startsWith("本") ||
                keyword.startsWith("上") || keyword.startsWith("昨") ||
                keyword.startsWith("前");
    }

    /**
     * 根据关键字查找时间范围枚举
     *
     * @param keyword 关键字（如 "近7天"、"最近一小时"、"今天"）
     * @return 对应的 TimeRange，不存在返回 null
     */
    public static TimeRange fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return getKeywordMap().get(keyword);
    }

    /**
     * 判断是否为已注册的时间范围关键字
     *
     * @param keyword 关键字
     * @return true 如果是时间范围关键字
     */
    public static boolean isKeyword(String keyword) {
        return keyword != null && getKeywordMap().containsKey(keyword);
    }

    /**
     * 获取所有关键字映射（只读）
     * <p>
     * key：关键字文本（如 "近7天"、"最近7天"），value：对应的 TimeRange 枚举
     *
     * @return 不可变的关键字映射
     */
    public static Map<String, TimeRange> getAllKeywords() {
        if (READ_ONLY_MAP == null) {
            synchronized (TimeRange.class) {
                if (READ_ONLY_MAP == null) {
                    READ_ONLY_MAP = Collections.unmodifiableMap(getKeywordMap());
                }
            }
        }
        return READ_ONLY_MAP;
    }

    /**
     * 获取或初始化关键字查找表
     */
    private static Map<String, TimeRange> getKeywordMap() {
        if (KEYWORD_MAP == null) {
            synchronized (TimeRange.class) {
                if (KEYWORD_MAP == null) {
                    Map<String, TimeRange> map = new HashMap<>();
                    for (TimeRange range : values()) {
                        // 注册主关键字
                        map.put(range.keyword, range);
                        // 注册别名
                        for (String alias : range.aliases) {
                            map.put(alias, range);
                        }
                    }
                    KEYWORD_MAP = map;
                }
            }
        }
        return KEYWORD_MAP;
    }
}
