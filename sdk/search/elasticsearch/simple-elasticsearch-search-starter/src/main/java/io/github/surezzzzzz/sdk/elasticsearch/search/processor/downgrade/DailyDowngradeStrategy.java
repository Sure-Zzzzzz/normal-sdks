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
 * 日粒度降级策略
 * LEVEL_0：具体日期索引
 * LEVEL_1：月级通配符（yyyy.MM.*）
 * LEVEL_2：年级通配符（yyyy.*）
 * LEVEL_3：全通配符（prefix*）
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class DailyDowngradeStrategy implements DowngradeStrategy {

    @Override
    public String[] apply(String indexPrefix, LocalDate fromDate, LocalDate toDate,
                          String datePattern, DowngradeLevel level) {
        switch (level) {
            case LEVEL_0:
                return generateDailyIndices(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_1:
                return generateMonthlyWildcards(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_2:
                return generateYearlyWildcards(indexPrefix, fromDate, toDate, datePattern);
            case LEVEL_3:
                return new String[]{indexPrefix + "*"};
            default:
                throw new UnsupportedDowngradeLevelException(
                        ErrorCode.UNSUPPORTED_DOWNGRADE_LEVEL,
                        String.format(ErrorMessage.UNSUPPORTED_DOWNGRADE_LEVEL, level, DateGranularity.DAY),
                        level,
                        DateGranularity.DAY
                );
        }
    }

    @Override
    public DateGranularity granularity() {
        return DateGranularity.DAY;
    }

    private String[] generateDailyIndices(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (totalDays > 365) {
            log.warn("Date range spans {} days, generating {} daily indices may impact performance.", totalDays, totalDays);
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern);
        Set<String> indices = new LinkedHashSet<>();
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            indices.add(indexPrefix + current.format(formatter));
            current = current.plusDays(1);
        }
        return indices.toArray(new String[0]);
    }

    private String[] generateMonthlyWildcards(String indexPrefix, LocalDate fromDate, LocalDate toDate, String datePattern) {
        String monthPattern = IndexDateHelper.buildMonthPattern(datePattern);
        String separator = IndexDateHelper.extractSeparator(datePattern);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(monthPattern);
        Set<String> wildcards = new LinkedHashSet<>();
        YearMonth current = YearMonth.from(fromDate);
        YearMonth end = YearMonth.from(toDate);
        while (!current.isAfter(end)) {
            wildcards.add(indexPrefix + current.format(formatter) + separator + "*");
            current = current.plusMonths(1);
        }
        return wildcards.toArray(new String[0]);
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
