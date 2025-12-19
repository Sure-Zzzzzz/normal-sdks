package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.IndexRouteProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IndexRouteProcessor 单元测试
 * 测试按年、月、日三种粒度的日期分割索引路由
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class IndexRouteProcessorTest {

    private IndexRouteProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new IndexRouteProcessor();
    }

    @Test
    @DisplayName("按天分割 - yyyy.MM.dd 格式")
    void testDailyGranularityWithDot() {
        // 构建测试数据
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("daily_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM.dd")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-01-15T00:00:00")
                .to("2025-01-17T23:59:59")
                .build();

        // 执行路由
        String[] indices = processor.route(metadata, dateRange);

        // 验证结果
        log.info("Daily routing result: {}", Arrays.toString(indices));
        assertEquals(3, indices.length, "应该生成3个按天分割的索引");
        assertEquals("log_2025.01.15", indices[0]);
        assertEquals("log_2025.01.16", indices[1]);
        assertEquals("log_2025.01.17", indices[2]);
    }

    @Test
    @DisplayName("按天分割 - yyyy-MM-dd 格式（横杠分隔符）")
    void testDailyGranularityWithDash() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("daily_log")
                .indexName("log-*")
                .dateSplit(true)
                .datePattern("yyyy-MM-dd")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-01-15")
                .to("2025-01-17")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Daily routing result (dash): {}", Arrays.toString(indices));
        assertEquals(3, indices.length);
        assertEquals("log-2025-01-15", indices[0]);
        assertEquals("log-2025-01-16", indices[1]);
        assertEquals("log-2025-01-17", indices[2]);
    }

    @Test
    @DisplayName("按月分割 - yyyy.MM 格式")
    void testMonthlyGranularityWithDot() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("monthly_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-01-15T00:00:00")
                .to("2025-03-20T23:59:59")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Monthly routing result: {}", Arrays.toString(indices));
        assertEquals(3, indices.length, "应该生成3个按月分割的索引");
        assertEquals("log_2025.01", indices[0]);
        assertEquals("log_2025.02", indices[1]);
        assertEquals("log_2025.03", indices[2]);
    }

    @Test
    @DisplayName("按月分割 - yyyy-MM 格式（横杠分隔符）")
    void testMonthlyGranularityWithDash() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("monthly_log")
                .indexName("monthly-*")
                .dateSplit(true)
                .datePattern("yyyy-MM")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2024-10-01")
                .to("2025-02-28")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Monthly routing result (dash): {}", Arrays.toString(indices));
        assertEquals(5, indices.length, "应该生成5个按月分割的索引（2024-10到2025-02）");
        assertEquals("monthly-2024-10", indices[0]);
        assertEquals("monthly-2024-11", indices[1]);
        assertEquals("monthly-2024-12", indices[2]);
        assertEquals("monthly-2025-01", indices[3]);
        assertEquals("monthly-2025-02", indices[4]);
    }

    @Test
    @DisplayName("按月分割 - yyyyMM 格式（无分隔符）")
    void testMonthlyGranularityNoSeparator() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("monthly_compact")
                .indexName("compact_*")
                .dateSplit(true)
                .datePattern("yyyyMM")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-01-01")
                .to("2025-03-31")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Monthly routing result (no separator): {}", Arrays.toString(indices));
        assertEquals(3, indices.length);
        assertEquals("compact_202501", indices[0]);
        assertEquals("compact_202502", indices[1]);
        assertEquals("compact_202503", indices[2]);
    }

    @Test
    @DisplayName("按年分割 - yyyy 格式")
    void testYearlyGranularity() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("yearly_archive")
                .indexName("archive_*")
                .dateSplit(true)
                .datePattern("yyyy")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2023-06-15T00:00:00")
                .to("2025-08-20T23:59:59")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Yearly routing result: {}", Arrays.toString(indices));
        assertEquals(3, indices.length, "应该生成3个按年分割的索引");
        assertEquals("archive_2023", indices[0]);
        assertEquals("archive_2024", indices[1]);
        assertEquals("archive_2025", indices[2]);
    }

    @Test
    @DisplayName("按年分割 - 跨越多年")
    void testYearlyGranularityMultipleYears() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("yearly_data")
                .indexName("data-*")
                .dateSplit(true)
                .datePattern("yyyy")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2020-01-01")
                .to("2025-12-31")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Yearly routing result (multi-year): {}", Arrays.toString(indices));
        assertEquals(6, indices.length, "应该生成6个按年分割的索引（2020-2025）");
        assertEquals("data-2020", indices[0]);
        assertEquals("data-2021", indices[1]);
        assertEquals("data-2022", indices[2]);
        assertEquals("data-2023", indices[3]);
        assertEquals("data-2024", indices[4]);
        assertEquals("data-2025", indices[5]);
    }

    @Test
    @DisplayName("单日查询 - 应该只生成1个索引")
    void testSingleDayQuery() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("daily_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM.dd")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-01-15T00:00:00")
                .to("2025-01-15T23:59:59")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Single day routing result: {}", Arrays.toString(indices));
        assertEquals(1, indices.length, "单日查询应该只生成1个索引");
        assertEquals("log_2025.01.15", indices[0]);
    }

    @Test
    @DisplayName("单月查询 - 应该只生成1个索引")
    void testSingleMonthQuery() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("monthly_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-03-01T00:00:00")
                .to("2025-03-31T23:59:59")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Single month routing result: {}", Arrays.toString(indices));
        assertEquals(1, indices.length, "单月查询应该只生成1个索引");
        assertEquals("log_2025.03", indices[0]);
    }

    @Test
    @DisplayName("非日期分割索引 - 返回原索引名")
    void testNonDateSplitIndex() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("normal_index")
                .indexName("products")
                .dateSplit(false)
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-01-01")
                .to("2025-12-31")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Non date-split routing result: {}", Arrays.toString(indices));
        assertEquals(1, indices.length, "非日期分割索引应该只返回1个索引");
        assertEquals("products", indices[0]);
    }

    @Test
    @DisplayName("日期分割索引但不提供日期范围 - 返回通配符索引")
    void testDateSplitIndexWithoutDateRange() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("daily_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM.dd")
                .build();

        String[] indices = processor.route(metadata, null);

        log.info("Date-split without range routing result: {}", Arrays.toString(indices));
        assertEquals(1, indices.length, "没有日期范围时应该返回通配符索引");
        assertEquals("log_*", indices[0]);
    }

    @Test
    @DisplayName("跨月边界查询 - 验证月末到月初")
    void testCrossMonthBoundary() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("daily_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM.dd")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-01-30")
                .to("2025-02-02")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Cross month boundary routing result: {}", Arrays.toString(indices));
        assertEquals(4, indices.length);
        assertEquals("log_2025.01.30", indices[0]);
        assertEquals("log_2025.01.31", indices[1]);
        assertEquals("log_2025.02.01", indices[2]);
        assertEquals("log_2025.02.02", indices[3]);
    }

    @Test
    @DisplayName("跨年边界查询 - 验证年末到年初")
    void testCrossYearBoundary() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("monthly_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2024-11-15")
                .to("2025-02-20")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Cross year boundary routing result: {}", Arrays.toString(indices));
        assertEquals(4, indices.length, "应该生成4个月的索引（2024-11到2025-02）");
        assertEquals("log_2024.11", indices[0]);
        assertEquals("log_2024.12", indices[1]);
        assertEquals("log_2025.01", indices[2]);
        assertEquals("log_2025.02", indices[3]);
    }

    @Test
    @DisplayName("复杂索引前缀 - 多层级前缀")
    void testComplexIndexPrefix() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("app_log")
                .indexName("app-prod-log-*")
                .dateSplit(true)
                .datePattern("yyyy.MM.dd")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-06-10")
                .to("2025-06-12")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Complex prefix routing result: {}", Arrays.toString(indices));
        assertEquals(3, indices.length);
        assertEquals("app-prod-log-2025.06.10", indices[0]);
        assertEquals("app-prod-log-2025.06.11", indices[1]);
        assertEquals("app-prod-log-2025.06.12", indices[2]);
    }

    @Test
    @DisplayName("异常场景 - 缺少日期格式配置")
    void testMissingDatePattern() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("broken_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern(null)  // 缺少日期格式
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-01-01")
                .to("2025-01-03")
                .build();

        // 应该抛出异常
        Exception exception = assertThrows(RuntimeException.class, () -> {
            processor.route(metadata, dateRange);
        });

        log.info("Expected exception: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("Index routing failed"));
    }

    @Test
    @DisplayName("异常场景 - 无效的日期格式")
    void testInvalidDateFormat() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("daily_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM.dd")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("invalid-date")  // 无效的日期
                .to("2025-01-03")
                .build();

        // 应该抛出异常
        Exception exception = assertThrows(RuntimeException.class, () -> {
            processor.route(metadata, dateRange);
        });

        log.info("Expected exception: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("Index routing failed"));
    }

    @Test
    @DisplayName("边界场景 - 起止日期相同（按月）")
    void testSameDateRangeMonthly() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("monthly_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2025-03-15")
                .to("2025-03-15")
                .build();

        String[] indices = processor.route(metadata, dateRange);

        log.info("Same date range monthly result: {}", Arrays.toString(indices));
        assertEquals(1, indices.length);
        assertEquals("log_2025.03", indices[0]);
    }

    @Test
    @DisplayName("性能测试 - 大范围日期查询（1年按天）")
    void testLargeRangeDailyQuery() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("daily_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM.dd")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2024-01-01")
                .to("2024-12-31")
                .build();

        long startTime = System.currentTimeMillis();
        String[] indices = processor.route(metadata, dateRange);
        long endTime = System.currentTimeMillis();

        log.info("Large range daily query: {} indices in {}ms", indices.length, (endTime - startTime));
        assertEquals(366, indices.length, "2024年是闰年，应该有366天");
        assertEquals("log_2024.01.01", indices[0]);
        assertEquals("log_2024.12.31", indices[365]);
    }

    @Test
    @DisplayName("性能测试 - 大范围日期查询（10年按月）")
    void testLargeRangeMonthlyQuery() {
        IndexMetadata metadata = IndexMetadata.builder()
                .alias("monthly_log")
                .indexName("log_*")
                .dateSplit(true)
                .datePattern("yyyy.MM")
                .build();

        QueryRequest.DateRange dateRange = QueryRequest.DateRange.builder()
                .from("2015-01-01")
                .to("2024-12-31")
                .build();

        long startTime = System.currentTimeMillis();
        String[] indices = processor.route(metadata, dateRange);
        long endTime = System.currentTimeMillis();

        log.info("Large range monthly query: {} indices in {}ms", indices.length, (endTime - startTime));
        assertEquals(120, indices.length, "10年应该有120个月");
        assertEquals("log_2015.01", indices[0]);
        assertEquals("log_2024.12", indices[119]);
    }
}
