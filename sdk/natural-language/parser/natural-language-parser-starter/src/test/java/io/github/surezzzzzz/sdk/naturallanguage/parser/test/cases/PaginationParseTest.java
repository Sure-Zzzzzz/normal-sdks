package io.github.surezzzzzz.sdk.naturallanguage.parser.test.cases;

import io.github.surezzzzzz.sdk.naturallanguage.parser.model.Intent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.PaginationIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.QueryIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.NLParser;
import io.github.surezzzzzz.sdk.naturallanguage.parser.test.NaturalLanguageParserTestApplication;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.NLTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分页解析测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = NaturalLanguageParserTestApplication.class)
public class PaginationParseTest {

    private NLParser parser;

    @BeforeEach
    public void setUp() {
        parser = new NLParser(new NLTokenizer());
    }

    @Test
    @DisplayName("测试1: 基础limit - 返回前10条")
    public void testBasicLimit() {
        String query = "年龄大于18，返回前10条";
        log.info("\n========================================");
        log.info("测试1: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(10, pagination.getLimit());
        assertNull(pagination.getOffset());
        assertNull(pagination.getPage());
        assertNull(pagination.getSize());
        assertNull(pagination.getContinueSearch());
    }

    @Test
    @DisplayName("测试2: offset + limit - 跳过20条，返回10条")
    public void testOffsetAndLimit() {
        String query = "年龄大于18，跳过20条，返回10条";
        log.info("\n========================================");
        log.info("测试2: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(10, pagination.getLimit());
        assertEquals(20, pagination.getOffset());
        assertNull(pagination.getPage());
        assertNull(pagination.getSize());
    }

    @Test
    @DisplayName("测试3: page + size - 第3页，每页10条")
    public void testPageAndSize() {
        String query = "年龄大于18，第3页，每页10条";
        log.info("\n========================================");
        log.info("测试3: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(3, pagination.getPage());
        assertEquals(10, pagination.getSize());
        assertEquals(20, pagination.getOffset()); // 第3页 = 跳过前20条
        assertEquals(10, pagination.getLimit()); // size也作为limit
    }

    @Test
    @DisplayName("测试4: 起始位置 - 从第21条开始，返回10条")
    public void testFromPosition() {
        String query = "年龄大于18，从第21条开始，返回10条";
        log.info("\n========================================");
        log.info("测试4: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(10, pagination.getLimit());
        assertEquals(20, pagination.getOffset()); // 从第21条 = 跳过前20条
        assertNull(pagination.getPage());
        assertNull(pagination.getSize());
    }

    @Test
    @DisplayName("测试5: 范围表达 - 返回第21到30条")
    public void testRange() {
        String query = "年龄大于18，返回第21到30条";
        log.info("\n========================================");
        log.info("测试5: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(20, pagination.getOffset()); // 第21条 = 跳过前20条
        assertEquals(10, pagination.getLimit()); // 30-21+1 = 10条
        assertNull(pagination.getPage());
        assertNull(pagination.getSize());
    }

    @Test
    @DisplayName("测试6: 续查请求 - 继续查询，返回10条（search_after）")
    public void testContinueSearch() {
        String query = "年龄大于18，继续查询，返回10条";
        log.info("\n========================================");
        log.info("测试6: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(10, pagination.getLimit());
        assertTrue(pagination.getContinueSearch());
        assertNull(pagination.getOffset());
        assertNull(pagination.getPage());
    }

    @Test
    @DisplayName("测试7: 英文关键词 - skip 20, limit 10")
    public void testEnglishKeywords() {
        String query = "age > 18, skip 20, limit 10";
        log.info("\n========================================");
        log.info("测试7: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(10, pagination.getLimit());
        assertEquals(20, pagination.getOffset());
    }

    @Test
    @DisplayName("测试8: 多种分隔符 - 中文顿号")
    public void testChineseDelimiter() {
        String query = "年龄大于18、跳过20条、返回10条";
        log.info("\n========================================");
        log.info("测试8: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(10, pagination.getLimit());
        assertEquals(20, pagination.getOffset());
    }

    @Test
    @DisplayName("测试9: 只有page没有size，但有limit")
    public void testPageWithLimit() {
        String query = "年龄大于18，第3页，返回10条";
        log.info("\n========================================");
        log.info("测试9: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(3, pagination.getPage());
        assertEquals(10, pagination.getSize());
        assertEquals(20, pagination.getOffset());
        assertEquals(10, pagination.getLimit());
    }

    @Test
    @DisplayName("测试10: 范围表达 - 第21至30条")
    public void testRangeWithZhi() {
        String query = "年龄大于18，返回第21至30条";
        log.info("\n========================================");
        log.info("测试10: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(20, pagination.getOffset());
        assertEquals(10, pagination.getLimit());
    }

    @Test
    @DisplayName("测试11: 续查 - 接着查")
    public void testJiezhaSearch() {
        String query = "年龄大于18，接着查，返回10条";
        log.info("\n========================================");
        log.info("测试11: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertEquals(10, pagination.getLimit());
        assertTrue(pagination.getContinueSearch());
    }

    @Test
    @DisplayName("测试12: 续查 - 接着")
    public void testNextPage() {
        String query = "年龄大于18，接着";
        log.info("\n========================================");
        log.info("测试12: {}", query);
        Intent intent = parser.parse(query);
        assertInstanceOf(QueryIntent.class, intent);

        QueryIntent q = (QueryIntent) intent;
        PaginationIntent pagination = q.getPagination();

        log.info("解析结果: {}", pagination);
        log.info("========================================\n");

        assertNotNull(pagination);
        assertTrue(pagination.getContinueSearch());
    }
}
