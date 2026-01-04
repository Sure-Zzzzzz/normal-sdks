package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

import java.time.temporal.ChronoUnit;

/**
 * 时间范围枚举
 * 预定义常用的时间范围快捷表达式
 * <p>
 * SDK 只负责识别关键字并返回对应的枚举值，
 * 具体如何计算时间由业务层决定（可使用 amount 和 unit 元数据）
 *
 * @author surezzzzzz
 */
public enum TimeRange {

    // ========== 分钟级 ==========
    LAST_5_MINUTES("近5分钟", 5, ChronoUnit.MINUTES),
    LAST_10_MINUTES("近10分钟", 10, ChronoUnit.MINUTES),
    LAST_15_MINUTES("近15分钟", 15, ChronoUnit.MINUTES),
    LAST_30_MINUTES("近30分钟", 30, ChronoUnit.MINUTES),

    // ========== 小时级 ==========
    LAST_1_HOUR("近1小时", 1, ChronoUnit.HOURS),
    LAST_6_HOURS("近6小时", 6, ChronoUnit.HOURS),
    LAST_12_HOURS("近12小时", 12, ChronoUnit.HOURS),
    LAST_24_HOURS("近24小时", 24, ChronoUnit.HOURS),

    // ========== 天级 ==========
    LAST_1_DAY("近1天", 1, ChronoUnit.DAYS),
    LAST_3_DAYS("近3天", 3, ChronoUnit.DAYS),
    LAST_7_DAYS("近7天", 7, ChronoUnit.DAYS),

    // ========== 周级 ==========
    LAST_1_WEEK("近1周", 1, ChronoUnit.WEEKS),
    LAST_2_WEEKS("近2周", 2, ChronoUnit.WEEKS),

    // ========== 月级（重点：匹配业务场景）==========
    LAST_1_MONTH("近1个月", 1, ChronoUnit.MONTHS),
    LAST_2_MONTHS("近2个月", 2, ChronoUnit.MONTHS),
    LAST_3_MONTHS("近三个月", 3, ChronoUnit.MONTHS),
    LAST_6_MONTHS("近半年", 6, ChronoUnit.MONTHS),

    // ========== 年级 ==========
    LAST_1_YEAR("近1年", 1, ChronoUnit.YEARS),
    LAST_2_YEARS("近2年", 2, ChronoUnit.YEARS),
    LAST_3_YEARS("近3年", 3, ChronoUnit.YEARS),

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
     * 中文关键字
     */
    private final String keyword;

    /**
     * 数量（可为负数表示过去）
     */
    private final int amount;

    /**
     * 时间单位
     */
    private final ChronoUnit unit;

    TimeRange(String keyword, int amount, ChronoUnit unit) {
        this.keyword = keyword;
        this.amount = amount;
        this.unit = unit;
    }

    public String getKeyword() {
        return keyword;
    }

    public int getAmount() {
        return amount;
    }

    public ChronoUnit getUnit() {
        return unit;
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
}
