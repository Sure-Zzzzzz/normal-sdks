package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DateGranularity;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateGranularity 枚举单元测试
 * 测试日期粒度检测和日期递增逻辑
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class DateGranularityTest {

    @Test
    @DisplayName("检测粒度 - yyyy.MM.dd 应识别为按天")
    void testDetectDay_DotSeparator() {
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyy.MM.dd");
        log.info("Detected granularity for 'yyyy.MM.dd': {}", granularity);
        assertEquals(DateGranularity.DAY, granularity);
    }

    @Test
    @DisplayName("检测粒度 - yyyy-MM-dd 应识别为按天")
    void testDetectDay_DashSeparator() {
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyy-MM-dd");
        log.info("Detected granularity for 'yyyy-MM-dd': {}", granularity);
        assertEquals(DateGranularity.DAY, granularity);
    }

    @Test
    @DisplayName("检测粒度 - yyyyMMdd 应识别为按天")
    void testDetectDay_NoSeparator() {
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyyMMdd");
        log.info("Detected granularity for 'yyyyMMdd': {}", granularity);
        assertEquals(DateGranularity.DAY, granularity);
    }

    @Test
    @DisplayName("检测粒度 - yyyy.MM 应识别为按月")
    void testDetectMonth_DotSeparator() {
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyy.MM");
        log.info("Detected granularity for 'yyyy.MM': {}", granularity);
        assertEquals(DateGranularity.MONTH, granularity);
    }

    @Test
    @DisplayName("检测粒度 - yyyy-MM 应识别为按月")
    void testDetectMonth_DashSeparator() {
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyy-MM");
        log.info("Detected granularity for 'yyyy-MM': {}", granularity);
        assertEquals(DateGranularity.MONTH, granularity);
    }

    @Test
    @DisplayName("检测粒度 - yyyyMM 应识别为按月")
    void testDetectMonth_NoSeparator() {
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyyMM");
        log.info("Detected granularity for 'yyyyMM': {}", granularity);
        assertEquals(DateGranularity.MONTH, granularity);
    }

    @Test
    @DisplayName("检测粒度 - yyyy 应识别为按年")
    void testDetectYear() {
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyy");
        log.info("Detected granularity for 'yyyy': {}", granularity);
        assertEquals(DateGranularity.YEAR, granularity);
    }

    @Test
    @DisplayName("检测粒度 - 大写格式 YYYY.MM.DD 应识别为按天")
    void testDetectDay_Uppercase() {
        DateGranularity granularity = DateGranularity.detectFromPattern("YYYY.MM.DD");
        log.info("Detected granularity for 'YYYY.MM.DD': {}", granularity);
        assertEquals(DateGranularity.DAY, granularity);
    }

    @Test
    @DisplayName("检测粒度 - 混合大小写 Yyyy.Mm 应识别为按月")
    void testDetectMonth_MixedCase() {
        DateGranularity granularity = DateGranularity.detectFromPattern("Yyyy.Mm");
        log.info("Detected granularity for 'Yyyy.Mm': {}", granularity);
        assertEquals(DateGranularity.MONTH, granularity);
    }

    @Test
    @DisplayName("检测粒度 - 空字符串应默认为按天")
    void testDetectDefault_EmptyString() {
        DateGranularity granularity = DateGranularity.detectFromPattern("");
        log.info("Detected granularity for empty string: {}", granularity);
        assertEquals(DateGranularity.DAY, granularity);
    }

    @Test
    @DisplayName("检测粒度 - null应默认为按天")
    void testDetectDefault_Null() {
        DateGranularity granularity = DateGranularity.detectFromPattern(null);
        log.info("Detected granularity for null: {}", granularity);
        assertEquals(DateGranularity.DAY, granularity);
    }

    @Test
    @DisplayName("检测粒度 - 特殊格式 yyyy/MM/dd 应识别为按天")
    void testDetectDay_SlashSeparator() {
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyy/MM/dd");
        log.info("Detected granularity for 'yyyy/MM/dd': {}", granularity);
        assertEquals(DateGranularity.DAY, granularity);
    }

    @Test
    @DisplayName("检测粒度 - 优先级测试：有日期则为按天（即使包含年月）")
    void testDetectPriority_DayFirst() {
        // 即使格式包含年月日，应该识别为按天（最细粒度）
        DateGranularity granularity = DateGranularity.detectFromPattern("yyyy.MM.dd");
        assertEquals(DateGranularity.DAY, granularity);
    }

    @Test
    @DisplayName("日期递增 - 按天递增")
    void testIncrementDay() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        LocalDate nextDate = DateGranularity.DAY.increment(date);

        log.info("DAY increment: {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2025, 1, 16), nextDate);
    }

    @Test
    @DisplayName("日期递增 - 按天递增跨月")
    void testIncrementDay_CrossMonth() {
        LocalDate date = LocalDate.of(2025, 1, 31);
        LocalDate nextDate = DateGranularity.DAY.increment(date);

        log.info("DAY increment (cross month): {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2025, 2, 1), nextDate);
    }

    @Test
    @DisplayName("日期递增 - 按天递增跨年")
    void testIncrementDay_CrossYear() {
        LocalDate date = LocalDate.of(2024, 12, 31);
        LocalDate nextDate = DateGranularity.DAY.increment(date);

        log.info("DAY increment (cross year): {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2025, 1, 1), nextDate);
    }

    @Test
    @DisplayName("日期递增 - 按月递增")
    void testIncrementMonth() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        LocalDate nextDate = DateGranularity.MONTH.increment(date);

        log.info("MONTH increment: {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2025, 2, 15), nextDate);
    }

    @Test
    @DisplayName("日期递增 - 按月递增跨年")
    void testIncrementMonth_CrossYear() {
        LocalDate date = LocalDate.of(2024, 12, 15);
        LocalDate nextDate = DateGranularity.MONTH.increment(date);

        log.info("MONTH increment (cross year): {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2025, 1, 15), nextDate);
    }

    @Test
    @DisplayName("日期递增 - 按月递增处理不同天数")
    void testIncrementMonth_DifferentDays() {
        // 1月31日 + 1个月 = 2月28日（非闰年）
        LocalDate date = LocalDate.of(2025, 1, 31);
        LocalDate nextDate = DateGranularity.MONTH.increment(date);

        log.info("MONTH increment (different days): {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2025, 2, 28), nextDate);
    }

    @Test
    @DisplayName("日期递增 - 按月递增处理闰年")
    void testIncrementMonth_LeapYear() {
        // 2024年是闰年，1月31日 + 1个月 = 2月29日
        LocalDate date = LocalDate.of(2024, 1, 31);
        LocalDate nextDate = DateGranularity.MONTH.increment(date);

        log.info("MONTH increment (leap year): {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2024, 2, 29), nextDate);
    }

    @Test
    @DisplayName("日期递增 - 按年递增")
    void testIncrementYear() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        LocalDate nextDate = DateGranularity.YEAR.increment(date);

        log.info("YEAR increment: {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2026, 6, 15), nextDate);
    }

    @Test
    @DisplayName("日期递增 - 按年递增处理闰年")
    void testIncrementYear_LeapYear() {
        // 2024年2月29日 + 1年 = 2025年2月28日（2025年非闰年）
        LocalDate date = LocalDate.of(2024, 2, 29);
        LocalDate nextDate = DateGranularity.YEAR.increment(date);

        log.info("YEAR increment (leap year): {} -> {}", date, nextDate);
        assertEquals(LocalDate.of(2025, 2, 28), nextDate);
    }

    @Test
    @DisplayName("枚举属性 - 验证identifier字段")
    void testEnumIdentifier() {
        assertEquals('y', DateGranularity.YEAR.getIdentifier());
        assertEquals('m', DateGranularity.MONTH.getIdentifier());
        assertEquals('d', DateGranularity.DAY.getIdentifier());
    }

    @Test
    @DisplayName("枚举属性 - 验证description字段")
    void testEnumDescription() {
        assertEquals("按年分割", DateGranularity.YEAR.getDescription());
        assertEquals("按月分割", DateGranularity.MONTH.getDescription());
        assertEquals("按天分割", DateGranularity.DAY.getDescription());

        log.info("YEAR: {}", DateGranularity.YEAR.getDescription());
        log.info("MONTH: {}", DateGranularity.MONTH.getDescription());
        log.info("DAY: {}", DateGranularity.DAY.getDescription());
    }

    @Test
    @DisplayName("边界测试 - 连续递增验证")
    void testContinuousIncrement() {
        LocalDate start = LocalDate.of(2025, 1, 15);

        // 按天连续递增3次
        LocalDate date = start;
        for (int i = 0; i < 3; i++) {
            date = DateGranularity.DAY.increment(date);
        }
        assertEquals(LocalDate.of(2025, 1, 18), date);

        // 按月连续递增3次
        date = start;
        for (int i = 0; i < 3; i++) {
            date = DateGranularity.MONTH.increment(date);
        }
        assertEquals(LocalDate.of(2025, 4, 15), date);

        // 按年连续递增3次
        date = start;
        for (int i = 0; i < 3; i++) {
            date = DateGranularity.YEAR.increment(date);
        }
        assertEquals(LocalDate.of(2028, 1, 15), date);
    }
}
