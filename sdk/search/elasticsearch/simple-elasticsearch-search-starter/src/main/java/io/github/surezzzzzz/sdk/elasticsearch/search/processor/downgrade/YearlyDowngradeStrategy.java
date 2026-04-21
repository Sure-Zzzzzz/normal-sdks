package io.github.surezzzzzz.sdk.elasticsearch.search.processor.downgrade;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DateGranularity;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.UnsupportedDowngradeLevelException;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.IndexDateHelper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 年粒度降级策略
 * LEVEL_0：具体年份索引
 * LEVEL_1/2/3：全通配符（prefix*）
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class YearlyDowngradeStrategy implements DowngradeStrategy {

    @Override
    public String[] apply(String indexPrefix, LocalDate fromDate, LocalDate toDate,
                          String datePattern, DowngradeLevel level) {
        switch (level) {
            case LEVEL_0:
                return generateYearlyIndices(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_1:
            case LEVEL_2:
            case LEVEL_3:
                return new String[]{indexPrefix + "*"};
            default:
                throw new UnsupportedDowngradeLevelException(
                        ErrorCode.UNSUPPORTED_DOWNGRADE_LEVEL,
                        String.format(ErrorMessage.UNSUPPORTED_DOWNGRADE_LEVEL, level, DateGranularity.YEAR),
                        level,
                        DateGranularity.YEAR
                );
        }
    }

    @Override
    public DateGranularity granularity() {
        return DateGranularity.YEAR;
    }

    private String[] generateYearlyIndices(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        String yearPattern = IndexDateHelper.buildYearPattern(datePattern);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(yearPattern);
        Set<String> indices = new LinkedHashSet<>();
        int currentYear = fromDate.getYear();
        int endYear = toDate.getYear();
        while (currentYear <= endYear) {
            indices.add(indexPrefix + LocalDate.of(currentYear, 1, 1).format(formatter));
            currentYear++;
        }
        return indices.toArray(new String[0]);
    }
}
