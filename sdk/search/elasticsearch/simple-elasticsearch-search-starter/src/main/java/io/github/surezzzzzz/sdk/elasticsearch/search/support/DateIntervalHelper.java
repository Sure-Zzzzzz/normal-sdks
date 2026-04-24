package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;

/**
 * 日期间隔解析工具
 * 将 ES 聚合间隔字符串（如 "day"、"1h"）解析为 {@link DateHistogramInterval}
 *
 * <p>兼容性：{@code DateHistogramInterval.QUARTER} 在 ES 6.8.x 中不存在，
 * 通过静态反射初始化检测，不可用时降级为字符串构造器 {@code new DateHistogramInterval("1q")}。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class DateIntervalHelper {

    /**
     * QUARTER 常量（ES 7.x+），6.8.x 下为 null，降级为字符串构造
     */
    private static final DateHistogramInterval QUARTER;

    static {
        DateHistogramInterval quarter = null;
        try {
            quarter = (DateHistogramInterval) DateHistogramInterval.class.getField("QUARTER").get(null);
        } catch (Exception ignored) {
            log.debug("DateHistogramInterval.QUARTER not available (ES < 7.x), will use string constructor");
        }
        QUARTER = quarter;
    }

    private DateIntervalHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将字符串间隔解析为 DateHistogramInterval
     *
     * @param interval 间隔字符串，如 "day"、"1h"，null 时默认 DAY
     * @return DateHistogramInterval
     */
    public static DateHistogramInterval parse(String interval) {
        if (interval == null) {
            return DateHistogramInterval.DAY;
        }
        switch (interval.toLowerCase()) {
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_SECOND:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_S:
                return DateHistogramInterval.SECOND;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_MINUTE:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_M:
                return DateHistogramInterval.MINUTE;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_HOUR:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_H:
                return DateHistogramInterval.HOUR;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_DAY:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_D:
                return DateHistogramInterval.DAY;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_WEEK:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_W:
                return DateHistogramInterval.WEEK;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_MONTH:
                return DateHistogramInterval.MONTH;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_QUARTER:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_Q:
                return QUARTER != null ? QUARTER : new DateHistogramInterval(SimpleElasticsearchSearchConstant.DATE_INTERVAL_QUARTER_VALUE);
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_YEAR:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_Y:
                return DateHistogramInterval.YEAR;
            default:
                return new DateHistogramInterval(interval);
        }
    }
}
