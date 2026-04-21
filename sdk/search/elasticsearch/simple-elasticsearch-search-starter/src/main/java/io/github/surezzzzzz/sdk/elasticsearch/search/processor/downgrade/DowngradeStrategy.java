package io.github.surezzzzzz.sdk.elasticsearch.search.processor.downgrade;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DateGranularity;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;

import java.time.LocalDate;

/**
 * 降级策略接口
 * 根据降级级别生成对应的索引名称数组
 *
 * @author surezzzzzz
 */
public interface DowngradeStrategy {

    /**
     * 根据降级级别生成索引名称数组
     *
     * @param indexPrefix 索引前缀（如 "log_"）
     * @param fromDate    起始日期
     * @param toDate      结束日期
     * @param datePattern 日期格式（如 "yyyy.MM.dd"）
     * @param level       降级级别
     * @return 索引名称数组
     */
    String[] apply(String indexPrefix, LocalDate fromDate, LocalDate toDate,
                   String datePattern, DowngradeLevel level);

    /**
     * 支持的日期粒度
     *
     * @return DateGranularity
     */
    DateGranularity granularity();
}
