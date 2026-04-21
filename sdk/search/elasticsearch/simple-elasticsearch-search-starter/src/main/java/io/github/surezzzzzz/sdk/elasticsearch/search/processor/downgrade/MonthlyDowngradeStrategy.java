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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 月粒度降级策略
 * LEVEL_0：具体月份索引
 * LEVEL_1：年级通配符（yyyy.*）
 * LEVEL_2/3：全通配符（prefix*）
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class MonthlyDowngradeStrategy implements DowngradeStrategy {

    @Override
    public String[] apply(String indexPrefix, LocalDate fromDate, LocalDate toDate,
                          String datePattern, DowngradeLevel level) {
        switch (level) {
            case LEVEL_0:
                return generateMonthlyIndices(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_1:
                return generateYearlyWildcards(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_2:
            case LEVEL_3:
                return new String[]{indexPrefix + "*"};
            default:
                throw new UnsupportedDowngradeLevelException(
                        ErrorCode.UNSUPPORTED_DOWNGRADE_LEVEL,
                        String.format(ErrorMessage.UNSUPPORTED_DOWNGRADE_LEVEL, level, DateGranularity.MONTH),
                        level,
                        DateGranularity.MONTH
                );
        }
    }

    @Override
    public DateGranularity granularity() {
        return DateGranularity.MONTH;
    }

    private String[] generateMonthlyIndices(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        String monthPattern = IndexDateHelper.buildMonthPattern(datePattern);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(monthPattern);
        Set<String> indices = new LinkedHashSet<>();
        YearMonth current = YearMonth.from(fromDate);
        YearMonth end = YearMonth.from(toDate);
        while (!current.isAfter(end)) {
            indices.add(indexPrefix + current.format(formatter));
            current = current.plusMonths(1);
        }
        return indices.toArray(new String[0]);
    }

    private String[] generateYearlyWildcards(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        String yearPattern = IndexDateHelper.buildYearPattern(datePattern);
        String separator = IndexDateHelper.extractSeparator(datePattern);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(yearPattern);
        Set<String> wildcards = new LinkedHashSet<>();
        int currentYear = fromDate.getYear();
        int endYear = toDate.getYear();
        while (currentYear <= endYear) {
            wildcards.add(indexPrefix + LocalDate.of(currentYear, 1, 1).format(formatter) + separator + "*");
            currentYear++;
        }
        return wildcards.toArray(new String[0]);
    }
}
