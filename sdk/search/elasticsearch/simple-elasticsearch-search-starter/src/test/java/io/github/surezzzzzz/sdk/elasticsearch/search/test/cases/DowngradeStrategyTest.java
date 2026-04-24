package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DateGranularity;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.downgrade.DailyDowngradeStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.downgrade.MonthlyDowngradeStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.downgrade.YearlyDowngradeStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 降级策略单元测试
 * 覆盖三种粒度（日/月/年）× 四个降级级别（LEVEL_0~3）及跨年边界，不依赖 ES 连接
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class DowngradeStrategyTest {

    @Autowired
    private DailyDowngradeStrategy dailyStrategy;

    @Autowired
    private MonthlyDowngradeStrategy monthlyStrategy;

    @Autowired
    private YearlyDowngradeStrategy yearlyStrategy;

    // ==================== DailyDowngradeStrategy ====================

    @Test
    @DisplayName("日粒度 - granularity() 返回 DAY")
    void testDailyGranularity() {
        assertEquals(DateGranularity.DAY, dailyStrategy.granularity());
    }

    @Test
    @DisplayName("日粒度 LEVEL_0 - 生成具体日期索引")
    void testDailyLevel0() {
        String[] result = dailyStrategy.apply("log_", LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 1, 17), "yyyy.MM.dd", DowngradeLevel.LEVEL_0);

        log.info("Daily LEVEL_0: {}", Arrays.toString(result));
        assertEquals(3, result.length);
        assertEquals("log_2025.01.15", result[0]);
        assertEquals("log_2025.01.16", result[1]);
        assertEquals("log_2025.01.17", result[2]);
    }

    @Test
    @DisplayName("日粒度 LEVEL_1 - 生成月级通配符（跨月）")
    void testDailyLevel1() {
        String[] result = dailyStrategy.apply("log_", LocalDate.of(2025, 1, 20),
                LocalDate.of(2025, 3, 10), "yyyy.MM.dd", DowngradeLevel.LEVEL_1);

        log.info("Daily LEVEL_1: {}", Arrays.toString(result));
        assertEquals(3, result.length);
        assertEquals("log_2025.01.*", result[0]);
        assertEquals("log_2025.02.*", result[1]);
        assertEquals("log_2025.03.*", result[2]);
    }

    @Test
    @DisplayName("日粒度 LEVEL_1 - 同月内生成1个月级通配符")
    void testDailyLevel1SameMonth() {
        String[] result = dailyStrategy.apply("log_", LocalDate.of(2025, 3, 5),
                LocalDate.of(2025, 3, 25), "yyyy.MM.dd", DowngradeLevel.LEVEL_1);

        log.info("Daily LEVEL_1 same month: {}", Arrays.toString(result));
        assertEquals(1, result.length);
        assertEquals("log_2025.03.*", result[0]);
    }

    @Test
    @DisplayName("日粒度 LEVEL_1 - 跨年边界（12月→1月）通配符正确")
    void testDailyLevel1CrossYear() {
        String[] result = dailyStrategy.apply("log_", LocalDate.of(2024, 12, 1),
                LocalDate.of(2025, 1, 31), "yyyy.MM.dd", DowngradeLevel.LEVEL_1);

        log.info("Daily LEVEL_1 cross year: {}", Arrays.toString(result));
        assertEquals(2, result.length);
        assertEquals("log_2024.12.*", result[0]);
        assertEquals("log_2025.01.*", result[1]);
    }

    @Test
    @DisplayName("日粒度 LEVEL_2 - 生成年级通配符（跨年）")
    void testDailyLevel2() {
        String[] result = dailyStrategy.apply("log_", LocalDate.of(2024, 11, 1),
                LocalDate.of(2025, 3, 31), "yyyy.MM.dd", DowngradeLevel.LEVEL_2);

        log.info("Daily LEVEL_2: {}", Arrays.toString(result));
        assertEquals(2, result.length);
        assertEquals("log_2024.*", result[0]);
        assertEquals("log_2025.*", result[1]);
    }

    @Test
    @DisplayName("日粒度 LEVEL_2 - 单年内生成1个年级通配符")
    void testDailyLevel2SingleYear() {
        String[] result = dailyStrategy.apply("log_", LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31), "yyyy.MM.dd", DowngradeLevel.LEVEL_2);

        log.info("Daily LEVEL_2 single year: {}", Arrays.toString(result));
        assertEquals(1, result.length);
        assertEquals("log_2024.*", result[0]);
    }

    @Test
    @DisplayName("日粒度 LEVEL_3 - 生成全通配符")
    void testDailyLevel3() {
        String[] result = dailyStrategy.apply("log_", LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 12, 31), "yyyy.MM.dd", DowngradeLevel.LEVEL_3);

        log.info("Daily LEVEL_3: {}", Arrays.toString(result));
        assertEquals(1, result.length);
        assertEquals("log_*", result[0]);
    }

    @Test
    @DisplayName("日粒度 LEVEL_1 - 横杠分隔符")
    void testDailyLevel1DashSeparator() {
        String[] result = dailyStrategy.apply("log-", LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 2, 28), "yyyy-MM-dd", DowngradeLevel.LEVEL_1);

        log.info("Daily LEVEL_1 dash: {}", Arrays.toString(result));
        assertEquals(2, result.length);
        assertEquals("log-2025-01-*", result[0]);
        assertEquals("log-2025-02-*", result[1]);
    }

    // ==================== MonthlyDowngradeStrategy ====================

    @Test
    @DisplayName("月粒度 - granularity() 返回 MONTH")
    void testMonthlyGranularity() {
        assertEquals(DateGranularity.MONTH, monthlyStrategy.granularity());
    }

    @Test
    @DisplayName("月粒度 LEVEL_0 - 生成具体月份索引")
    void testMonthlyLevel0() {
        String[] result = monthlyStrategy.apply("log_", LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 3, 31), "yyyy.MM", DowngradeLevel.LEVEL_0);

        log.info("Monthly LEVEL_0: {}", Arrays.toString(result));
        assertEquals(3, result.length);
        assertEquals("log_2025.01", result[0]);
        assertEquals("log_2025.02", result[1]);
        assertEquals("log_2025.03", result[2]);
    }

    @Test
    @DisplayName("月粒度 LEVEL_1 - 生成年级通配符（跨年）")
    void testMonthlyLevel1() {
        String[] result = monthlyStrategy.apply("log_", LocalDate.of(2024, 6, 1),
                LocalDate.of(2025, 3, 31), "yyyy.MM", DowngradeLevel.LEVEL_1);

        log.info("Monthly LEVEL_1: {}", Arrays.toString(result));
        assertEquals(2, result.length);
        assertEquals("log_2024.*", result[0]);
        assertEquals("log_2025.*", result[1]);
    }

    @Test
    @DisplayName("月粒度 LEVEL_1 - 同年内生成1个年级通配符")
    void testMonthlyLevel1SameYear() {
        String[] result = monthlyStrategy.apply("log_", LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31), "yyyy.MM", DowngradeLevel.LEVEL_1);

        log.info("Monthly LEVEL_1 same year: {}", Arrays.toString(result));
        assertEquals(1, result.length);
        assertEquals("log_2025.*", result[0]);
    }

    @Test
    @DisplayName("月粒度 LEVEL_2 - 生成全通配符")
    void testMonthlyLevel2() {
        String[] result = monthlyStrategy.apply("log_", LocalDate.of(2023, 1, 1),
                LocalDate.of(2025, 12, 31), "yyyy.MM", DowngradeLevel.LEVEL_2);

        log.info("Monthly LEVEL_2: {}", Arrays.toString(result));
        assertEquals(1, result.length);
        assertEquals("log_*", result[0]);
    }

    @Test
    @DisplayName("月粒度 LEVEL_3 - 生成全通配符")
    void testMonthlyLevel3() {
        String[] result = monthlyStrategy.apply("log_", LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31), "yyyy.MM", DowngradeLevel.LEVEL_3);

        log.info("Monthly LEVEL_3: {}", Arrays.toString(result));
        assertEquals(1, result.length);
        assertEquals("log_*", result[0]);
    }

    // ==================== YearlyDowngradeStrategy ====================

    @Test
    @DisplayName("年粒度 - granularity() 返回 YEAR")
    void testYearlyGranularity() {
        assertEquals(DateGranularity.YEAR, yearlyStrategy.granularity());
    }

    @Test
    @DisplayName("年粒度 LEVEL_0 - 生成具体年份索引（跨年）")
    void testYearlyLevel0() {
        String[] result = yearlyStrategy.apply("archive_", LocalDate.of(2023, 1, 1),
                LocalDate.of(2025, 12, 31), "yyyy", DowngradeLevel.LEVEL_0);

        log.info("Yearly LEVEL_0: {}", Arrays.toString(result));
        assertEquals(3, result.length);
        assertEquals("archive_2023", result[0]);
        assertEquals("archive_2024", result[1]);
        assertEquals("archive_2025", result[2]);
    }

    @Test
    @DisplayName("年粒度 LEVEL_0 - 单年查询")
    void testYearlyLevel0SingleYear() {
        String[] result = yearlyStrategy.apply("data-", LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 11, 30), "yyyy", DowngradeLevel.LEVEL_0);

        log.info("Yearly LEVEL_0 single year: {}", Arrays.toString(result));
        assertEquals(1, result.length);
        assertEquals("data-2025", result[0]);
    }

    @Test
    @DisplayName("年粒度 LEVEL_1 - 生成全通配符")
    void testYearlyLevel1() {
        String[] result = yearlyStrategy.apply("archive_", LocalDate.of(2023, 1, 1),
                LocalDate.of(2025, 12, 31), "yyyy", DowngradeLevel.LEVEL_1);

        log.info("Yearly LEVEL_1: {}", Arrays.toString(result));
        assertEquals(1, result.length);
        assertEquals("archive_*", result[0]);
    }

    @Test
    @DisplayName("年粒度 LEVEL_2/3 - 生成全通配符")
    void testYearlyLevel2And3() {
        String[] level2 = yearlyStrategy.apply("archive_", LocalDate.of(2023, 1, 1),
                LocalDate.of(2025, 12, 31), "yyyy", DowngradeLevel.LEVEL_2);
        String[] level3 = yearlyStrategy.apply("archive_", LocalDate.of(2023, 1, 1),
                LocalDate.of(2025, 12, 31), "yyyy", DowngradeLevel.LEVEL_3);

        log.info("Yearly LEVEL_2: {}", Arrays.toString(level2));
        log.info("Yearly LEVEL_3: {}", Arrays.toString(level3));
        assertArrayEquals(new String[]{"archive_*"}, level2);
        assertArrayEquals(new String[]{"archive_*"}, level3);
    }
}
