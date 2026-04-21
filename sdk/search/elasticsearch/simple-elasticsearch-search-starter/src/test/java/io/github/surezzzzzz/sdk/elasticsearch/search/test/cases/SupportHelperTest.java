package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.support.DslCompatibilityHelper;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.IndexDateHelper;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IndexDateHelper / DslCompatibilityHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class SupportHelperTest {

    // ==================== IndexDateHelper ====================

    @Test
    @DisplayName("parseDate - 纯日期格式")
    void testParseDatePlain() {
        LocalDate result = IndexDateHelper.parseDate("2025-03-15");
        log.info("parseDate plain: {}", result);
        assertEquals(LocalDate.of(2025, 3, 15), result);
    }

    @Test
    @DisplayName("parseDate - ISO 日期时间格式")
    void testParseDateIso() {
        LocalDate result = IndexDateHelper.parseDate("2025-03-15T00:00:00");
        log.info("parseDate ISO: {}", result);
        assertEquals(LocalDate.of(2025, 3, 15), result);
    }

    @Test
    @DisplayName("parseDate - 无效格式抛出异常")
    void testParseDateInvalid() {
        Exception ex = assertThrows(RuntimeException.class, () -> IndexDateHelper.parseDate("not-a-date"));
        log.info("parseDate invalid exception: {}", ex.getMessage());
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("extractIndexPrefix - 去掉末尾通配符")
    void testExtractIndexPrefixWithWildcard() {
        String result = IndexDateHelper.extractIndexPrefix("log_*");
        log.info("extractIndexPrefix with wildcard: {}", result);
        assertEquals("log_", result);
    }

    @Test
    @DisplayName("extractIndexPrefix - 无通配符原样返回")
    void testExtractIndexPrefixNoWildcard() {
        String result = IndexDateHelper.extractIndexPrefix("log_2025.01");
        log.info("extractIndexPrefix no wildcard: {}", result);
        assertEquals("log_2025.01", result);
    }

    @Test
    @DisplayName("extractSeparator - 点分隔符")
    void testExtractSeparatorDot() {
        String result = IndexDateHelper.extractSeparator("yyyy.MM.dd");
        log.info("extractSeparator dot: {}", result);
        assertEquals(".", result);
    }

    @Test
    @DisplayName("extractSeparator - 横杠分隔符")
    void testExtractSeparatorDash() {
        String result = IndexDateHelper.extractSeparator("yyyy-MM-dd");
        log.info("extractSeparator dash: {}", result);
        assertEquals("-", result);
    }

    @Test
    @DisplayName("extractSeparator - 无分隔符返回空字符串")
    void testExtractSeparatorNone() {
        String result = IndexDateHelper.extractSeparator("yyyyMMdd");
        log.info("extractSeparator none: {}", result);
        assertEquals("", result);
    }

    @Test
    @DisplayName("buildMonthPattern - yyyy.MM.dd → yyyy.MM")
    void testBuildMonthPatternDot() {
        String result = IndexDateHelper.buildMonthPattern("yyyy.MM.dd");
        log.info("buildMonthPattern dot: {}", result);
        assertEquals("yyyy.MM", result);
    }

    @Test
    @DisplayName("buildMonthPattern - yyyy-MM-dd → yyyy-MM")
    void testBuildMonthPatternDash() {
        String result = IndexDateHelper.buildMonthPattern("yyyy-MM-dd");
        log.info("buildMonthPattern dash: {}", result);
        assertEquals("yyyy-MM", result);
    }

    @Test
    @DisplayName("buildMonthPattern - yyyyMMdd → yyyyMM")
    void testBuildMonthPatternNoSeparator() {
        String result = IndexDateHelper.buildMonthPattern("yyyyMMdd");
        log.info("buildMonthPattern no separator: {}", result);
        assertEquals("yyyyMM", result);
    }

    @Test
    @DisplayName("buildYearPattern - yyyy.MM.dd → yyyy")
    void testBuildYearPatternDot() {
        String result = IndexDateHelper.buildYearPattern("yyyy.MM.dd");
        log.info("buildYearPattern dot: {}", result);
        assertEquals("yyyy", result);
    }

    @Test
    @DisplayName("buildYearPattern - yyyy-MM → yyyy")
    void testBuildYearPatternFromMonth() {
        String result = IndexDateHelper.buildYearPattern("yyyy-MM");
        log.info("buildYearPattern from month: {}", result);
        assertEquals("yyyy", result);
    }

    @Test
    @DisplayName("buildYearPattern - yyyyMMdd → yyyy")
    void testBuildYearPatternNoSeparator() {
        String result = IndexDateHelper.buildYearPattern("yyyyMMdd");
        log.info("buildYearPattern no separator: {}", result);
        assertEquals("yyyy", result);
    }

    // ==================== DslCompatibilityHelper ====================

    @Test
    @DisplayName("removeEs7OnlyCompositeFields - null 输入原样返回")
    void testRemoveEs7FieldsNull() {
        String result = DslCompatibilityHelper.removeEs7OnlyCompositeFields(null);
        log.info("removeEs7Fields null: {}", result);
        assertNull(result);
    }

    @Test
    @DisplayName("removeEs7OnlyCompositeFields - 不含 missing_bucket 原样返回")
    void testRemoveEs7FieldsNoMissingBucket() {
        String dsl = "{\"aggs\":{\"by_date\":{\"date_histogram\":{\"field\":\"@timestamp\"}}}}";
        String result = DslCompatibilityHelper.removeEs7OnlyCompositeFields(dsl);
        log.info("removeEs7Fields no missing_bucket: {}", result);
        assertEquals(dsl, result);
    }

    @Test
    @DisplayName("removeEs7OnlyCompositeFields - 移除 missing_bucket:false（前置逗号）")
    void testRemoveEs7FieldsMissingBucketFalseLeadingComma() {
        String dsl = "{\"terms\":{\"field\":\"status\"},\"missing_bucket\":false}";
        String result = DslCompatibilityHelper.removeEs7OnlyCompositeFields(dsl);
        log.info("removeEs7Fields missing_bucket false leading comma: {}", result);
        assertFalse(result.contains("missing_bucket"), "结果不应包含 missing_bucket");
    }

    @Test
    @DisplayName("removeEs7OnlyCompositeFields - 移除 missing_bucket:true（后置逗号）")
    void testRemoveEs7FieldsMissingBucketTrueTrailingComma() {
        String dsl = "{\"missing_bucket\":true,\"terms\":{\"field\":\"status\"}}";
        String result = DslCompatibilityHelper.removeEs7OnlyCompositeFields(dsl);
        log.info("removeEs7Fields missing_bucket true trailing comma: {}", result);
        assertFalse(result.contains("missing_bucket"), "结果不应包含 missing_bucket");
    }

    @Test
    @DisplayName("removeEs7OnlyCompositeFields - 移除 missing_bucket（无逗号）")
    void testRemoveEs7FieldsMissingBucketAlone() {
        String dsl = "{\"missing_bucket\":false}";
        String result = DslCompatibilityHelper.removeEs7OnlyCompositeFields(dsl);
        log.info("removeEs7Fields missing_bucket alone: {}", result);
        assertFalse(result.contains("missing_bucket"), "结果不应包含 missing_bucket");
    }

    @Test
    @DisplayName("removeEs7OnlyCompositeFields - 多个 missing_bucket 全部移除")
    void testRemoveEs7FieldsMultipleMissingBucket() {
        String dsl = "{\"sources\":[{\"missing_bucket\":false,\"field\":\"a\"},{\"missing_bucket\":true,\"field\":\"b\"}]}";
        String result = DslCompatibilityHelper.removeEs7OnlyCompositeFields(dsl);
        log.info("removeEs7Fields multiple: {}", result);
        assertFalse(result.contains("missing_bucket"), "结果不应包含任何 missing_bucket");
    }
}
