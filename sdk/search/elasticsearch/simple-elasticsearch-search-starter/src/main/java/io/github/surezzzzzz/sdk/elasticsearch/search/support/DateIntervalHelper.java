package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;

/**
 * 日期间隔解析工具
 * 将 ES 聚合间隔字符串（如 "day"、"1h"）解析为 {@link DateHistogramInterval}
 *
 * @author surezzzzzz
 */
public final class DateIntervalHelper {

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
                return DateHistogramInterval.QUARTER;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_YEAR:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_Y:
                return DateHistogramInterval.YEAR;
            default:
                return new DateHistogramInterval(interval);
        }
    }
}
