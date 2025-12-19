package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

import lombok.Getter;

import java.time.LocalDate;

/**
 * 日期分割粒度枚举
 * 用于索引路由时判断按年、月还是日分割
 *
 * @author surezzzzzz
 */
@Getter
public enum DateGranularity {

    /**
     * 按年分割
     */
    YEAR('y', "按年分割"),

    /**
     * 按月分割
     */
    MONTH('m', "按月分割"),

    /**
     * 按天分割
     */
    DAY('d', "按天分割");

    /**
     * 识别字符（用于从日期格式中识别粒度）
     */
    private final char identifier;

    /**
     * 中文描述
     */
    private final String description;

    DateGranularity(char identifier, String description) {
        this.identifier = identifier;
        this.description = description;
    }

    /**
     * 根据日期格式自动检测分割粒度
     *
     * @param datePattern 日期格式（如 yyyy.MM.dd, yyyy.MM, yyyy）
     * @return 分割粒度
     */
    public static DateGranularity detectFromPattern(String datePattern) {
        if (datePattern == null || datePattern.isEmpty()) {
            return DAY;  // 默认按天
        }

        // 移除分隔符，只看格式字符
        String normalized = datePattern.toLowerCase()
                .replaceAll("[^ymd]", "");

        // 按优先级判断：日 > 月 > 年
        if (normalized.contains(String.valueOf(DAY.identifier))) {
            return DAY;
        } else if (normalized.contains(String.valueOf(MONTH.identifier))) {
            return MONTH;
        } else if (normalized.contains(String.valueOf(YEAR.identifier))) {
            return YEAR;
        }

        // 默认按天
        return DAY;
    }

    /**
     * 根据粒度递增日期
     *
     * @param date 当前日期
     * @return 递增后的日期
     */
    public LocalDate increment(LocalDate date) {
        switch (this) {
            case YEAR:
                return date.plusYears(1);
            case MONTH:
                return date.plusMonths(1);
            case DAY:
            default:
                return date.plusDays(1);
        }
    }
}
