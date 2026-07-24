package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionHintsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResult;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ExpressionParseException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.MappingException;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.TimeRangeEnd;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.service.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor.ExpressionToQueryConditionVisitor;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor.ExpressionVisitorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SearchTestProfilesResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.helper.EsApiHelper;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.ConditionExpressionParser;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AdvancedExpressionService 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles(resolver = SearchTestProfilesResolver.class)
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class ExpressionTest {

    private static final String NL_USER_INDEX = "test_nl_user_index";
    @Autowired
    private ExpressionService expressionService;
    @Autowired
    private ConditionExpressionParser expressionParser;
    @Autowired
    private ExpressionVisitorRegistry visitorRegistry;
    @Autowired
    private MappingManager mappingManager;
    @Autowired
    private QueryDslBuilder queryDslBuilder;

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) {
        EsApiHelper.deleteIndex(registry, "primary", NL_USER_INDEX);
        EsApiHelper.createIndex(registry, "primary", NL_USER_INDEX,
                "{\"properties\":{" +
                        "\"name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}}," +
                        "\"age\":{\"type\":\"long\"}," +
                        "\"city\":{\"type\":\"keyword\"}," +
                        "\"status\":{\"type\":\"keyword\"}," +
                        "\"points\":{\"type\":\"long\"}," +
                        "\"eventTimestamp\":{\"type\":\"long\"}," +
                        "\"createTime\":{\"type\":\"date\"}," +
                        "\"orderId\":{\"type\":\"keyword\"}" +
                        "}}");
        log.info("✓ ExpressionTest: 已创建索引 {}", NL_USER_INDEX);
    }

    // ==================== ExpressionVisitorRegistry ====================

    @Test
    @DisplayName("registry - 有 field-mapping 的索引预建了 visitor")
    void testRegistryWithMapping() {
        ExpressionToQueryConditionVisitor visitor = visitorRegistry.resolve("test_order_index");
        log.info("visitor for test_order_index: {}", visitor);
        assertNotNull(visitor);
    }

    @Test
    @DisplayName("registry - 无 field-mapping 的索引返回默认 visitor")
    void testRegistryDefaultVisitor() {
        ExpressionToQueryConditionVisitor v1 = visitorRegistry.resolve("test_log_*");
        ExpressionToQueryConditionVisitor v2 = visitorRegistry.resolve(null);
        ExpressionToQueryConditionVisitor v3 = visitorRegistry.resolve("不存在的索引");
        log.info("default visitor: {}", v1);
        // 三个都应该是同一个默认实例
        assertSame(v1, v2);
        assertSame(v1, v3);
    }

    // ==================== translate - 基本操作符 ====================

    @Test
    @DisplayName("translate - 等于")
    void testTranslateEq() {
        QueryCondition result = expressionService.translate("事件类型 = \"mock-value\"", null);
        log.info("eq: {}", result);
        assertNotNull(result);
        assertEquals("事件类型", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("mock-value", result.getValue());
    }

    @Test
    @DisplayName("translate - 不等于")
    void testTranslateNe() {
        QueryCondition result = expressionService.translate("处置状态 != \"已忽略\"", null);
        log.info("ne: {}", result);
        assertEquals("ne", result.getOp());
        assertEquals("已忽略", result.getValue());
    }

    @Test
    @DisplayName("translate - 数值大于等于")
    void testTranslateGte() {
        QueryCondition result = expressionService.translate("事件数量 >= 10", null);
        log.info("gte: {}", result);
        assertEquals("gte", result.getOp());
        assertEquals("10", result.getValue());
    }

    @Test
    @DisplayName("translate - IN 多值")
    void testTranslateIn() {
        QueryCondition result = expressionService.translate("事件等级 IN (\"一级\", \"二级\")", null);
        log.info("in: {}", result);
        assertEquals("in", result.getOp());
        assertNotNull(result.getValues());
        assertEquals(2, result.getValues().size());
        assertTrue(result.getValues().contains("一级"));
        assertTrue(result.getValues().contains("二级"));
    }

    @Test
    @DisplayName("translate - NOT IN")
    void testTranslateNotIn() {
        QueryCondition result = expressionService.translate("协议 NOT IN (\"HTTP\", \"HTTPS\")", null);
        log.info("not_in: {}", result);
        assertEquals("not_in", result.getOp());
        assertEquals(2, result.getValues().size());
    }

    @Test
    @DisplayName("translate - LIKE 模糊匹配")
    void testTranslateLike() {
        QueryCondition result = expressionService.translate("示例字段 LIKE \"mock-value\"", null);
        log.info("like: {}", result);
        assertEquals("like", result.getOp());
        assertEquals("mock-value", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT LIKE")
    void testTranslateNotLike() {
        QueryCondition result = expressionService.translate("示例字段 NOT LIKE \"sample-value\"", null);
        log.info("not_like: {}", result);
        assertEquals("not_like", result.getOp());
        assertEquals("sample-value", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT PREFIX LIKE（中文 NOT）")
    void testTranslateNotPrefixLike() {
        QueryCondition result = expressionService.translate("名称 非 前缀 包含 \"张\"", null);
        log.info("not_prefix: {}", result);
        assertEquals("not_prefix", result.getOp());
        assertEquals("张", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT PREFIX LIKE（英文 NOT）")
    void testTranslateNotPrefixLikeEnglish() {
        QueryCondition result = expressionService.translate("name NOT PREFIX LIKE \"John\"", null);
        log.info("not_prefix: {}", result);
        assertEquals("not_prefix", result.getOp());
        assertEquals("John", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT SUFFIX LIKE（中文 NOT）")
    void testTranslateNotSuffixLike() {
        QueryCondition result = expressionService.translate("邮箱 非 后缀 包含 \"@spam.com\"", null);
        log.info("not_suffix: {}", result);
        assertEquals("not_suffix", result.getOp());
        assertEquals("@spam.com", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT SUFFIX LIKE（英文 NOT）")
    void testTranslateNotSuffixLikeEnglish() {
        QueryCondition result = expressionService.translate("email NOT SUFFIX LIKE \"@spam.com\"", null);
        log.info("not_suffix: {}", result);
        assertEquals("not_suffix", result.getOp());
        assertEquals("@spam.com", result.getValue());
    }

    @Test
    @DisplayName("translate - IS NULL")
    void testTranslateIsNull() {
        QueryCondition result = expressionService.translate("事件标签 IS NULL", null);
        log.info("is_null: {}", result);
        assertEquals("is_null", result.getOp());
        assertNull(result.getValue());
    }

    @Test
    @DisplayName("translate - IS NOT NULL")
    void testTranslateIsNotNull() {
        QueryCondition result = expressionService.translate("事件标签 IS NOT NULL", null);
        log.info("is_not_null: {}", result);
        assertEquals("is_not_null", result.getOp());
    }

    @Test
    @DisplayName("translate - EXISTS")
    void testTranslateExists() {
        QueryCondition result = expressionService.translate("备注 EXISTS", null);
        log.info("exists: {}", result);
        assertEquals("exists", result.getOp(), "EXISTS 应翻译为 exists");
        assertEquals("备注", result.getField(), "字段名应原样透传");
    }

    @Test
    @DisplayName("translate - NOT EXISTS")
    void testTranslateNotExists() {
        QueryCondition result = expressionService.translate("备注 NOT EXISTS", null);
        log.info("not_exists: {}", result);
        assertEquals("not_exists", result.getOp(), "NOT EXISTS 应翻译为 not_exists");
        assertEquals("备注", result.getField(), "字段名应原样透传");
    }

    @Test
    @DisplayName("translate - NOT (EXISTS) 取反为 not_exists（修复 bug）")
    void testNegateExists() {
        QueryCondition result = expressionService.translate(
                "NOT (备注 EXISTS)", null);
        log.info("NOT (EXISTS): {}", result);
        assertEquals("not_exists", result.getOp(), "NOT (EXISTS) 应取反为 not_exists");
    }

    @Test
    @DisplayName("translate - NOT (NOT EXISTS) 取反为 exists（修复 bug）")
    void testNegateNotExists() {
        QueryCondition result = expressionService.translate(
                "NOT (备注 NOT EXISTS)", null);
        log.info("NOT (NOT EXISTS): {}", result);
        assertEquals("exists", result.getOp(), "NOT (NOT EXISTS) 应取反为 exists");
    }

    // ==================== translate - 逻辑组合 ====================

    @Test
    @DisplayName("translate - AND 组合")
    void testTranslateAnd() {
        QueryCondition result = expressionService.translate(
                "事件类型 = \"mock-value\" AND 事件数量 >= 10", null);
        log.info("and: {}", result);
        assertEquals("and", result.getLogic());
        assertNotNull(result.getConditions());
        assertEquals(2, result.getConditions().size());
    }

    @Test
    @DisplayName("translate - OR 组合")
    void testTranslateOr() {
        QueryCondition result = expressionService.translate(
                "事件类型 = \"mock-value\" OR 事件类型 = \"蠕虫\"", null);
        log.info("or: {}", result);
        assertEquals("or", result.getLogic());
        assertEquals(2, result.getConditions().size());
    }

    @Test
    @DisplayName("translate - 括号分组")
    void testTranslateParenthesis() {
        QueryCondition result = expressionService.translate(
                "(事件类型 = \"mock-value\" OR 事件类型 = \"蠕虫\") AND 事件数量 > 5", null);
        log.info("parenthesis: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());
        // 左侧是 OR 组合
        QueryCondition left = result.getConditions().get(0);
        assertEquals("or", left.getLogic());
    }

    @Test
    @DisplayName("translate - NOT 叶子节点取反")
    void testTranslateNotLeaf() {
        QueryCondition result = expressionService.translate(
                "NOT 示例字段 = \"mock-value\"", null);
        log.info("not leaf: {}", result);
        // NOT eq → ne
        assertEquals("ne", result.getOp());
        assertEquals("示例字段", result.getField());
        assertEquals("mock-value", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT 复合节点德摩根展开")
    void testTranslateNotComplex() {
        QueryCondition result = expressionService.translate(
                "NOT (事件类型 = \"mock-value\" AND 事件数量 >= 10)", null);
        log.info("not complex: {}", result);
        // NOT (A AND B) → (NOT A) OR (NOT B)
        assertEquals("or", result.getLogic());
        assertEquals(2, result.getConditions().size());
        assertEquals("ne", result.getConditions().get(0).getOp());
        assertEquals("lt", result.getConditions().get(1).getOp());
    }

    @Test
    @DisplayName("translate - NOT (PREFIX LIKE) 取反为 not_prefix")
    void testNegatePrefixLike() {
        QueryCondition result = expressionService.translate(
                "NOT (名称 PREFIX LIKE \"张\")", null);
        log.info("NOT (PREFIX LIKE): {}", result);
        assertEquals("not_prefix", result.getOp());
        assertEquals("张", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT (SUFFIX LIKE) 取反为 not_suffix")
    void testNegateSuffixLike() {
        QueryCondition result = expressionService.translate(
                "NOT (邮箱 SUFFIX LIKE \"@spam.com\")", null);
        log.info("NOT (SUFFIX LIKE): {}", result);
        assertEquals("not_suffix", result.getOp());
        assertEquals("@spam.com", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT (NOT LIKE) 取反为 like")
    void testNegateNotLike() {
        QueryCondition result = expressionService.translate(
                "NOT (name NOT LIKE \"mock-value\")", null);
        log.info("NOT (NOT LIKE): {}", result);
        assertEquals("like", result.getOp(), "NOT (NOT LIKE) 应恢复为 like");
        assertEquals("mock-value", result.getValue(), "LIKE 值应保持不变");
    }

    @Test
    @DisplayName("translate - NOT (NOT PREFIX LIKE) 取反为 prefix")
    void testNegateNotPrefixLike() {
        QueryCondition result = expressionService.translate(
                "NOT (name NOT PREFIX LIKE \"mock\")", null);
        log.info("NOT (NOT PREFIX LIKE): {}", result);
        assertEquals("prefix", result.getOp(), "NOT (NOT PREFIX LIKE) 应恢复为 prefix");
        assertEquals("mock", result.getValue(), "PREFIX 值应保持不变");
    }

    @Test
    @DisplayName("translate - NOT (NOT SUFFIX LIKE) 取反为 suffix")
    void testNegateNotSuffixLike() {
        QueryCondition result = expressionService.translate(
                "NOT (email NOT SUFFIX LIKE \"@example.test\")", null);
        log.info("NOT (NOT SUFFIX LIKE): {}", result);
        assertEquals("suffix", result.getOp(), "NOT (NOT SUFFIX LIKE) 应恢复为 suffix");
        assertEquals("@example.test", result.getValue(), "SUFFIX 值应保持不变");
    }

    // ==================== translate - 字段名映射 ====================

    @Test
    @DisplayName("translate - 字段名映射（从索引配置读取）")
    void testTranslateFieldMapping() {
        // test_order_index 在 application.yaml 中配置了 field-mapping
        QueryCondition result = expressionService.translate(
                "状态 = \"已完成\" AND 金额 >= 10", "test_order_index");
        log.info("field mapping: {}", result);

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("status", left.getField());
        assertEquals("amount", right.getField());
    }

    @Test
    @DisplayName("translate - 字段名映射找不到时原样透传")
    void testTranslateFieldMappingFallback() {
        QueryCondition result = expressionService.translate("未知字段 = \"值\"", "test_order_index");
        log.info("field mapping fallback: {}", result);
        assertEquals("未知字段", result.getField());
    }

    @Test
    @DisplayName("translate - 多个中文标签都映射到同一 ES 字段")
    void testTranslateFieldMappingMultipleLabels() {
        // "订单状态" 和 "状态" 都映射到 status
        QueryCondition r1 = expressionService.translate("状态 = \"已完成\"", "test_order_index");
        QueryCondition r2 = expressionService.translate("订单状态 = \"已完成\"", "test_order_index");
        log.info("label 1: {}", r1);
        log.info("label 2: {}", r2);
        assertEquals("status", r1.getField());
        assertEquals("status", r2.getField());
    }

    // ==================== translate - 时间范围 ====================

    @Test
    @DisplayName("translate - 时间范围 = 最近7天 转为 between")
    void testTranslateTimeRange() {
        QueryCondition result = expressionService.translate("创建时间 = 最近7天", "test_order_index");
        log.info("time range: {}", result);
        assertEquals("between", result.getOp());
        assertNotNull(result.getValues());
        assertEquals(2, result.getValues().size());
        log.info("from={}, to={}", result.getValues().get(0), result.getValues().get(1));
    }

    @Test
    @DisplayName("translate - long 时间字段使用 epoch seconds")
    void testTranslateLongTimeRange() {
        QueryCondition result = expressionService.translate("事件时间戳 = 最近7天", NL_USER_INDEX,
                TimeRangeEnd.TODAY_START, "Asia/Shanghai");
        log.info("long time range: {}", result);

        assertEquals("between", result.getOp(), "long 时间字段应生成 between 条件");
        assertEquals(2, result.getValues().size(), "long 时间范围应包含两个边界");
        assertTrue(result.getValues().get(0) instanceof Long, "long 时间范围起始值应为 epoch seconds Long");
        assertTrue(result.getValues().get(1) instanceof Long, "long 时间范围结束值应为 epoch seconds Long");
    }

    @Test
    @DisplayName("translate - long 时间字段范围补集保留 epoch seconds")
    void testTranslateLongTimeRangeOutside() {
        QueryCondition result = expressionService.translate("NOT (事件时间戳 = 最近7天)", NL_USER_INDEX,
                TimeRangeEnd.TODAY_START, "Asia/Shanghai");
        log.info("long time range outside: {}", result);

        assertEquals("or", result.getLogic(), "long 时间范围补集应生成 OR 条件");
        assertTrue(result.getConditions().get(0).getValue() instanceof Long,
                "long 时间范围补集起始边界应保留 Long");
        assertTrue(result.getConditions().get(1).getValue() instanceof Long,
                "long 时间范围补集结束边界应保留 Long");
    }

    @Test
    @DisplayName("translate - 时间范围 TODAY_START 截止当天零点")
    void testTranslateTimeRangeTodayStart() {
        QueryCondition result = expressionService.translate("创建时间 = 最近7天", NL_USER_INDEX,
                TimeRangeEnd.TODAY_START, "Asia/Shanghai");
        log.info("time range today start: {}", result);

        assertEquals("between", result.getOp(), "TODAY_START 应生成 between 条件");
        String end = String.valueOf(result.getValues().get(1));
        assertTrue(end.endsWith("T00:00:00"), "TODAY_START 应截止到指定时区的当天零点");
    }

    @Test
    @DisplayName("translate - 指定时区的 TODAY_START 精确转换 DATE 与 LONG 边界")
    void testTranslateTimeRangeTodayStartWithFixedClock() {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T17:20:30Z"), zoneId);
        ExpressionToQueryConditionVisitor visitor = new ExpressionToQueryConditionVisitor(
                null, mappingManager, NL_USER_INDEX, TimeRangeEnd.TODAY_START, clock);

        QueryCondition dateCondition = expressionParser.parse("createTime = 最近7天").accept(visitor);
        QueryCondition longCondition = expressionParser.parse("eventTimestamp = 最近7天").accept(visitor);
        log.info("fixed clock date condition={}, long condition={}", dateCondition, longCondition);

        assertEquals("2026-07-11T00:00:00", dateCondition.getValues().get(0),
                "DATE 起始边界应按 Asia/Shanghai 当天零点向前七天计算");
        assertEquals("2026-07-18T00:00:00", dateCondition.getValues().get(1),
                "DATE 结束边界应为 Asia/Shanghai 当天零点");
        assertEquals(Instant.parse("2026-07-10T16:00:00Z").getEpochSecond(), longCondition.getValues().get(0),
                "LONG 起始边界应为同一时区零点对应的 epoch seconds");
        assertEquals(Instant.parse("2026-07-17T16:00:00Z").getEpochSecond(), longCondition.getValues().get(1),
                "LONG 结束边界应为当天零点对应的 epoch seconds");
        assertTrue(longCondition.getValues().get(0) instanceof Long, "LONG 起始边界应保留 Long 类型");
        assertTrue(longCondition.getValues().get(1) instanceof Long, "LONG 结束边界应保留 Long 类型");

        QueryBuilder longQuery = queryDslBuilder.build(mappingManager.getMetadata(NL_USER_INDEX), longCondition);
        assertTrue(longQuery instanceof RangeQueryBuilder, "LONG 时间范围应生成 RangeQueryBuilder");
        RangeQueryBuilder rangeQuery = (RangeQueryBuilder) longQuery;
        assertEquals(longCondition.getValues().get(0), rangeQuery.from(), "最终 DSL 起始边界应保留数值");
        assertEquals(longCondition.getValues().get(1), rangeQuery.to(), "最终 DSL 结束边界应保留数值");
        assertTrue(rangeQuery.from() instanceof Long, "最终 DSL 起始边界应为 Long");
        assertTrue(rangeQuery.to() instanceof Long, "最终 DSL 结束边界应为 Long");
    }

    @Test
    @DisplayName("translate - long 时间范围补集与单边比较保留精确 epoch seconds")
    void testTranslateLongTimeRangeOperatorsWithFixedClock() {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T17:20:30Z"), zoneId);
        ExpressionToQueryConditionVisitor visitor = new ExpressionToQueryConditionVisitor(
                null, mappingManager, NL_USER_INDEX, TimeRangeEnd.TODAY_START, clock);
        Long from = Instant.parse("2026-07-10T16:00:00Z").getEpochSecond();
        Long end = Instant.parse("2026-07-17T16:00:00Z").getEpochSecond();

        QueryCondition notEqual = expressionParser.parse("eventTimestamp != 最近7天").accept(visitor);
        QueryCondition negatedEqual = expressionParser.parse("NOT (eventTimestamp = 最近7天)").accept(visitor);
        assertOutsideLongRange(notEqual, from, end, "long != 最近7天");
        assertOutsideLongRange(negatedEqual, from, end, "NOT (long = 最近7天)");

        QueryCondition doubleNegated = expressionParser.parse("NOT (eventTimestamp != 最近7天)").accept(visitor);
        assertEquals("and", doubleNegated.getLogic(), "NOT (long != 最近7天) 应恢复为区间内");
        assertEquals("gte", doubleNegated.getConditions().get(0).getOp(), "区间内下界应为 gte");
        assertEquals(from, doubleNegated.getConditions().get(0).getValue(), "区间内下界应保留 epoch seconds");
        assertEquals("lte", doubleNegated.getConditions().get(1).getOp(), "区间内上界应为 lte");
        assertEquals(end, doubleNegated.getConditions().get(1).getValue(), "区间内上界应保留 epoch seconds");

        assertLongTimeComparison(visitor, "eventTimestamp > 最近7天", "gt", from);
        assertLongTimeComparison(visitor, "eventTimestamp >= 最近7天", "gte", from);
        assertLongTimeComparison(visitor, "eventTimestamp < 最近7天", "lt", from);
        assertLongTimeComparison(visitor, "eventTimestamp <= 最近7天", "lte", from);
    }

    @Test
    @DisplayName("translate - 日历时间关键字按请求 IANA 时区截断")
    void testTranslateCalendarTimeRangeWithFixedClock() {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T17:20:30Z"), zoneId);
        ExpressionToQueryConditionVisitor visitor = new ExpressionToQueryConditionVisitor(
                null, mappingManager, NL_USER_INDEX, TimeRangeEnd.NOW, clock);

        QueryCondition today = expressionParser.parse("createTime = 今天").accept(visitor);
        QueryCondition currentWeek = expressionParser.parse("createTime = 本周").accept(visitor);

        assertEquals("2026-07-18T00:00:00", today.getValues().get(0), "今天的起始边界应按 Asia/Shanghai 计算");
        assertEquals("2026-07-18T01:20:30", today.getValues().get(1), "今天的结束边界应保留请求时区当前时间");
        assertEquals("2026-07-13T00:00:00", currentWeek.getValues().get(0), "本周起始边界应为请求时区周一零点");
        assertEquals("2026-07-18T01:20:30", currentWeek.getValues().get(1), "本周结束边界应保留请求时区当前时间");
    }

    @Test
    @DisplayName("translate - 旧重载默认使用 NOW 与系统时区")
    void testTranslateDefaultOverloadUsesNowAndSystemZone() {
        QueryCondition defaultResult = expressionService.translate("创建时间 = 最近7天", NL_USER_INDEX);
        QueryCondition explicitResult = expressionService.translate("创建时间 = 最近7天", NL_USER_INDEX,
                TimeRangeEnd.NOW, null);

        assertEquals("between", defaultResult.getOp(), "旧重载应继续生成时间范围");
        assertEquals("between", explicitResult.getOp(), "NOW 重载应生成时间范围");
        assertEquals(defaultResult.getValues().size(), explicitResult.getValues().size(), "两个重载应生成相同边界数量");
        assertTrue(String.valueOf(defaultResult.getValues().get(1)).contains("T"), "旧重载应保持 DATE 时间格式");
        assertTrue(String.valueOf(explicitResult.getValues().get(1)).contains("T"), "NOW 重载应保持 DATE 时间格式");
    }

    @Test
    @DisplayName("translate - 并发请求的时间范围上下文互不串扰")
    void testTranslateTimeRangeContextIsConcurrentSafe() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        long before = Instant.now().getEpochSecond();
        try {
            List<Callable<QueryCondition>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                tasks.add(() -> expressionService.translate("事件时间戳 = 最近7天", NL_USER_INDEX,
                        TimeRangeEnd.TODAY_START, "Asia/Shanghai"));
                tasks.add(() -> expressionService.translate("事件时间戳 = 最近7天", NL_USER_INDEX,
                        TimeRangeEnd.NOW, "UTC"));
            }

            List<Future<QueryCondition>> futures = executorService.invokeAll(tasks);
            long after = Instant.now().getEpochSecond();
            for (int i = 0; i < futures.size(); i++) {
                QueryCondition condition = futures.get(i).get(10, TimeUnit.SECONDS);
                assertEquals("between", condition.getOp(), "并发翻译应保留时间范围操作符");
                assertTrue(condition.getValues().get(0) instanceof Long, "并发翻译起始边界应保持 Long");
                assertTrue(condition.getValues().get(1) instanceof Long, "并发翻译结束边界应保持 Long");
                long end = (Long) condition.getValues().get(1);
                if (i % 2 == 0) {
                    assertEquals(0, Instant.ofEpochSecond(end).atZone(ZoneId.of("Asia/Shanghai")).getHour(),
                            "TODAY_START 上界应按 Shanghai 时区截断到零点");
                    assertEquals(0, Instant.ofEpochSecond(end).atZone(ZoneId.of("Asia/Shanghai")).getMinute(),
                            "TODAY_START 上界应按 Shanghai 时区截断到零点");
                } else {
                    assertTrue(end >= before && end <= after,
                            "UTC NOW 上界应是本次调用期间的当前 epoch seconds，不能被其他请求截断");
                }
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    private void assertOutsideLongRange(QueryCondition condition, Long from, Long end, String description) {
        assertEquals("or", condition.getLogic(), description + " 应生成区间补集");
        assertEquals(2, condition.getConditions().size(), description + " 应包含两个边界");
        assertEquals("lt", condition.getConditions().get(0).getOp(), description + " 下界外应使用 lt");
        assertEquals(from, condition.getConditions().get(0).getValue(), description + " 下界应保留 epoch seconds");
        assertEquals("gt", condition.getConditions().get(1).getOp(), description + " 上界外应使用 gt");
        assertEquals(end, condition.getConditions().get(1).getValue(), description + " 上界应保留 epoch seconds");
        assertTrue(condition.getConditions().get(0).getValue() instanceof Long, description + " 下界必须为 Long");
        assertTrue(condition.getConditions().get(1).getValue() instanceof Long, description + " 上界必须为 Long");
    }

    private void assertLongTimeComparison(ExpressionToQueryConditionVisitor visitor, String expression,
                                          String expectedOperator, Long expectedValue) {
        QueryCondition condition = expressionParser.parse(expression).accept(visitor);
        assertEquals(expectedOperator, condition.getOp(), expression + " 应保留比较运算符");
        assertEquals(expectedValue, condition.getValue(), expression + " 应使用时间范围起始 epoch seconds");
        assertTrue(condition.getValue() instanceof Long, expression + " 的边界必须为 Long");
    }

    @Test
    @DisplayName("translate - mapping 不可用时 DATE 时间范围回退 ISO 边界")
    void testTranslateTimeRangeFallsBackWhenMappingUnavailable() {
        MappingManager unavailableMappingManager = mock(MappingManager.class);
        SimpleElasticsearchSearchProperties.IndexConfig indexConfig =
                new SimpleElasticsearchSearchProperties.IndexConfig();
        indexConfig.setName("missing-index");
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T17:20:30Z"), ZoneId.of("Asia/Shanghai"));
        when(unavailableMappingManager.findIndexConfig("missing-index")).thenReturn(indexConfig);
        when(unavailableMappingManager.getMetadata("missing-index"))
                .thenThrow(new MappingException("SEARCH_MAPPING_001", "mapping unavailable"));
        ExpressionToQueryConditionVisitor visitor = new ExpressionToQueryConditionVisitor(
                null, unavailableMappingManager, "missing-index", TimeRangeEnd.TODAY_START, clock);

        QueryCondition condition = expressionParser.parse("createTime = 最近7天").accept(visitor);

        assertEquals("between", condition.getOp(), "mapping 不可用时仍应生成时间范围");
        assertEquals("2026-07-11T00:00:00", condition.getValues().get(0),
                "mapping 不可用时起始边界应回退 ISO 字符串");
        assertEquals("2026-07-18T00:00:00", condition.getValues().get(1),
                "mapping 不可用时结束边界应回退 ISO 字符串");
        verify(unavailableMappingManager).getMetadata("missing-index");
    }

    @Test
    @DisplayName("translate - 时间范围不等于转为区间外")
    void testTranslateTimeRangeNotEqual() {
        QueryCondition result = expressionService.translate("创建时间 != 最近7天", "test_order_index");
        log.info("time range not equal: {}", result);

        assertEquals("or", result.getLogic(), "时间范围不等于应生成 OR 区间补集");
        assertEquals(2, result.getConditions().size(), "时间范围补集应包含两个边界条件");
        assertEquals("lt", result.getConditions().get(0).getOp(), "第一个边界应小于开始时间");
        assertEquals("gt", result.getConditions().get(1).getOp(), "第二个边界应大于结束时间");
    }

    @Test
    @DisplayName("translate - NOT 时间范围等于转为区间外")
    void testNegateTimeRangeEqual() {
        QueryCondition result = expressionService.translate("NOT (创建时间 = 最近7天)", "test_order_index");
        log.info("NOT time range equal: {}", result);

        assertEquals("or", result.getLogic(), "NOT 时间范围等于应生成 OR 区间补集");
        assertEquals(2, result.getConditions().size(), "时间范围补集应包含两个边界条件");
        assertEquals("lt", result.getConditions().get(0).getOp(), "第一个边界应小于开始时间");
        assertEquals("gt", result.getConditions().get(1).getOp(), "第二个边界应大于结束时间");
    }

    @Test
    @DisplayName("translate - 双重否定时间范围恢复区间内")
    void testDoubleNegateTimeRange() {
        QueryCondition result = expressionService.translate("NOT (创建时间 != 最近7天)", "test_order_index");
        log.info("double negate time range: {}", result);

        assertEquals("and", result.getLogic(), "双重否定时间范围应生成 AND 区间内条件");
        assertEquals(2, result.getConditions().size(), "区间内条件应包含两个边界");
        assertEquals("gte", result.getConditions().get(0).getOp(), "第一个边界应大于等于开始时间");
        assertEquals("lte", result.getConditions().get(1).getOp(), "第二个边界应小于等于结束时间");
    }

    // ==================== validate ====================

    @Test
    @DisplayName("validate - 不读取 mapping")
    void testValidateDoesNotLoadMapping() {
        MappingManager mockedMappingManager = mock(MappingManager.class);
        ExpressionService service = new ExpressionService(expressionParser, visitorRegistry,
                new SimpleElasticsearchSearchProperties(), mockedMappingManager);

        ExpressionValidationResult result = service.validate("事件类型 = \"mock-value\"", NL_USER_INDEX);

        assertTrue(result.isValid(), "合法表达式应完成语法校验");
        verify(mockedMappingManager, never()).getMetadata(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("validate - 合法表达式")
    void testValidateValid() {
        // 必须传 index，normalize 后解析成功
        ExpressionValidationResult result = expressionService.validate(
                "事件类型 = \"mock-value\" AND 事件数量 >= 10", "test_nl_user_index");
        log.info("validate valid: {}", result);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
        assertEquals(-1, result.getErrorPosition());
    }

    @Test
    @DisplayName("validate - 语法错误")
    void testValidateInvalid() {
        ExpressionValidationResult result = expressionService.validate(
                "事件类型 = \"mock-value\" AND 事件数量 >=", "test_nl_user_index");
        log.info("validate invalid: {}", result);
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        log.info("error: {}", result.getErrorMessage());
    }

    @Test
    @DisplayName("validate - 空表达式")
    void testValidateEmpty() {
        ExpressionValidationResult result = expressionService.validate("", "test_nl_user_index");
        log.info("validate empty: {}", result);
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("validate - 支持中文 label")
    void testValidateWithChineseLabel() {
        log.info("========== 测试：validate 支持中文 label ==========");

        // 订单ID 替换后解析成功
        ExpressionValidationResult result = expressionService.validate(
                "订单ID = 'xxx'", "test_order_index");
        log.info("result: {}", result);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());

        log.info("✓ validate 支持中文 label 测试通过");
    }

    @Test
    @DisplayName("validate - 中文 label + 中文运算符")
    void testValidateWithChineseLabelAndOperator() {
        log.info("========== 测试：validate 中文 label + 中文运算符 ==========");

        ExpressionValidationResult result = expressionService.validate(
                "订单ID等于'xxx' 且 城市大于'北京'", "test_nl_user_index");
        log.info("result: {}", result);
        assertTrue(result.isValid());

        log.info("✓ validate 中文 label + 运算符测试通过");
    }

    // ==================== translate - 异常 ====================

    @Test
    @DisplayName("translate - 非法 IANA 时区抛出 ExpressionParseException")
    void testTranslateThrowsOnInvalidTimeZone() {
        ExpressionParseException exception = assertThrows(ExpressionParseException.class, () ->
                expressionService.translate("创建时间 = 最近7天", NL_USER_INDEX, TimeRangeEnd.NOW, "invalid-zone"));
        log.info("invalid time zone error: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("表达式时间时区无效"), "错误信息应说明时区非法");
    }

    @Test
    @DisplayName("translate - 语法错误抛出 ExpressionParseException")
    void testTranslateThrowsOnSyntaxError() {
        assertThrows(ExpressionParseException.class, () ->
                expressionService.translate("事件类型 = ", null));
    }

    // ==================== getHints ====================

    @Test
    @DisplayName("getHints - 全局提示不依赖索引（运算符、时间范围、值规则）")
    void testGetHintsGlobalHints() {
        log.info("========== 测试：getHints 全局提示 ==========");

        ExpressionHintsResponse hints = expressionService.getHints(null);
        log.info("hints: {}", hints);

        assertNotNull(hints);
        assertNotNull(hints.getOperators());
        assertNotNull(hints.getTimeRanges());
        assertNotNull(hints.getValueRules());

        // 运算符不为空，且无重复
        assertFalse(hints.getOperators().isEmpty(), "运算符列表不应为空");
        long distinctOps = hints.getOperators().stream()
                .map(ExpressionHintsResponse.OperatorHint::getOp)
                .distinct().count();
        assertEquals(hints.getOperators().size(), distinctOps, "运算符列表不应有重复");

        // 包含核心运算符
        List<String> ops = hints.getOperators().stream()
                .map(ExpressionHintsResponse.OperatorHint::getOp)
                .collect(java.util.stream.Collectors.toList());
        assertTrue(ops.contains("="), "应包含 =");
        assertTrue(ops.contains("!="), "应包含 !=");
        assertTrue(ops.contains("IN"), "应包含 IN");
        assertTrue(ops.contains("NOT IN"), "应包含 NOT IN");
        assertTrue(ops.contains("LIKE"), "应包含 LIKE");
        assertTrue(ops.contains("IS NULL"), "应包含 IS NULL");
        assertTrue(ops.contains("AND"), "应包含 AND");
        assertTrue(ops.contains("OR"), "应包含 OR");
        assertTrue(ops.contains("NOT"), "应包含 NOT");

        // 时间范围数量与 TimeRange 枚举一致
        assertEquals(TimeRange.values().length, hints.getTimeRanges().size(),
                "时间范围数量应与 TimeRange 枚举一致");

        log.info("✓ getHints 全局提示测试通过");
    }

    @Test
    @DisplayName("getHints - 运算符包含中文别名")
    void testGetHintsOperatorsWithChinese() {
        log.info("========== 测试：getHints 运算符中文别名 ==========");

        ExpressionHintsResponse hints = expressionService.getHints(null);

        boolean hasEqWithChinese = hints.getOperators().stream()
                .anyMatch(op -> "=".equals(op.getOp()) && op.getChinese() != null && !op.getChinese().isEmpty());
        assertTrue(hasEqWithChinese, "等于运算符应有中文别名");

        boolean hasAndWithChinese = hints.getOperators().stream()
                .anyMatch(op -> "AND".equals(op.getOp()) && op.getChinese() != null && !op.getChinese().isEmpty());
        assertTrue(hasAndWithChinese, "AND 运算符应有中文别名");

        log.info("✓ 运算符中文别名测试通过");
    }

    @Test
    @DisplayName("getHints - 时间范围包含所有主关键字")
    void testGetHintsTimeRanges() {
        log.info("========== 测试：getHints 时间范围主关键字 ==========");

        ExpressionHintsResponse hints = expressionService.getHints(null);
        List<String> timeRanges = hints.getTimeRanges();
        log.info("timeRanges: {}", timeRanges);

        assertTrue(timeRanges.contains("近7天"), "应包含 '近7天'");
        assertTrue(timeRanges.contains("近1小时"), "应包含 '近1小时'");
        assertTrue(timeRanges.contains("近3个月"), "应包含 '近3个月'");
        assertTrue(timeRanges.contains("今天"), "应包含 '今天'");
        assertTrue(timeRanges.contains("近1个月"), "应包含 '近1个月'");

        for (TimeRange range : TimeRange.values()) {
            assertTrue(timeRanges.contains(range.getKeyword()),
                    "应包含主关键字: " + range.getKeyword());
        }

        log.info("✓ 时间范围主关键字测试通过");
    }

    @Test
    @DisplayName("getHints - 值规则正确")
    void testGetHintsValueRules() {
        log.info("========== 测试：getHints 值规则 ==========");

        ExpressionHintsResponse hints = expressionService.getHints(null);
        ExpressionHintsResponse.ValueRules rules = hints.getValueRules();
        log.info("valueRules: {}", rules);

        assertTrue(rules.isStringNeedsQuote(), "字符串值需要加引号");
        assertTrue(rules.isNumberNoQuote(), "数字不需要引号");
        assertTrue(rules.getSupportedQuotes().contains("'"), "支持单引号");
        assertTrue(rules.getSupportedQuotes().contains("\""), "支持双引号");
        assertTrue(rules.getBooleanKeywords().contains("true"), "布尔关键字包含 true");
        assertTrue(rules.getBooleanKeywords().contains("假"), "布尔关键字包含 假");

        log.info("✓ 值规则测试通过");
    }

    @Test
    @DisplayName("getHints - 所有运算符都有非空中文描述")
    void testGetHintsAllOperatorsHaveChinese() {
        ExpressionHintsResponse hints = expressionService.getHints(null);
        hints.getOperators().forEach(op ->
                assertNotNull(op.getChinese(),
                        "运算符 " + op.getOp() + " 的 chinese 不应为 null"));
        hints.getOperators().forEach(op ->
                assertFalse(op.getChinese().isEmpty(),
                        "运算符 " + op.getOp() + " 的 chinese 不应为空字符串"));
    }

    @Test
    @DisplayName("getHints - 不存在的索引字段为空但全局提示仍返回")
    void testGetHintsNonExistentIndex() {
        log.info("========== 测试：getHints 不存在的索引 ==========");

        ExpressionHintsResponse hints = expressionService.getHints("不存在的索引");
        log.info("hints for non-existent index: {}", hints);

        assertNotNull(hints);
        assertTrue(hints.getFields() == null || hints.getFields().isEmpty(),
                "不存在索引的字段列表应为空");
        assertNotNull(hints.getOperators());
        assertNotNull(hints.getTimeRanges());

        log.info("✓ 不存在索引测试通过");
    }

    @Test
    @DisplayName("getHints - 敏感字段被正确排除")
    void testGetHintsSensitiveFieldsExcluded() {
        log.info("========== 测试：getHints 敏感字段排除 ==========");

        ExpressionHintsResponse hints = expressionService.getHints("test_employee");
        log.info("hints for test_employee: {}", hints);

        assertNotNull(hints);
        List<ExpressionHintsResponse.FieldHint> fields = hints.getFields();
        assertNotNull(fields);
        log.info("fields returned: {}", fields.stream()
                .map(ExpressionHintsResponse.FieldHint::getName)
                .collect(java.util.stream.Collectors.toList()));

        // 验证非敏感字段存在
        assertTrue(fields.stream().anyMatch(f -> "emp_id".equals(f.getName())),
                "应包含 emp_id");
        assertTrue(fields.stream().anyMatch(f -> "emp_name".equals(f.getName())),
                "应包含 emp_name");
        assertTrue(fields.stream().anyMatch(f -> "department".equals(f.getName())),
                "应包含 department");
        assertTrue(fields.stream().anyMatch(f -> "join_date".equals(f.getName())),
                "应包含 join_date");
        assertTrue(fields.stream().anyMatch(f -> "phone".equals(f.getName())),
                "应包含 phone");

        // 验证敏感字段被排除
        assertFalse(fields.stream().anyMatch(f -> "salary".equals(f.getName())),
                "salary 是敏感字段应被排除");
        assertFalse(fields.stream().anyMatch(f -> "id_card".equals(f.getName())),
                "id_card 是敏感字段应被排除");

        // 验证标签列表正确
        fields.stream()
                .filter(f -> "emp_id".equals(f.getName()))
                .findFirst()
                .ifPresent(f -> {
                    assertNotNull(f.getLabel());
                    assertEquals(2, f.getLabel().size());
                    assertTrue(f.getLabel().contains("员工ID"));
                    assertTrue(f.getLabel().contains("工号"));
                });

        log.info("✓ 敏感字段排除测试通过");
    }

    @Test
    @DisplayName("translate - 同时有 field-mapping 和 sensitive-fields 的索引翻译正常")
    void testTranslateWithFieldMappingAndSensitiveFields() {
        log.info("========== 测试：translate 混合配置索引 ==========");

        // 使用中文标签翻译（非敏感字段）
        QueryCondition result1 = expressionService.translate(
                "员工姓名 = '张三' AND 部门 = '研发部'",
                "test_employee");
        log.info("result1: {}", result1);
        assertNotNull(result1);
        assertEquals("and", result1.getLogic());

        // 验证字段映射正确
        QueryCondition cond1 = result1.getConditions().get(0);
        QueryCondition cond2 = result1.getConditions().get(1);
        assertEquals("emp_name", cond1.getField());
        assertEquals("department", cond2.getField());

        // 使用工号标签
        QueryCondition result2 = expressionService.translate(
                "工号 = 'E001'",
                "test_employee");
        log.info("result2: {}", result2);
        assertEquals("emp_id", result2.getField());

        log.info("✓ 混合配置索引翻译测试通过");
    }

    // ==================== v1.6.4 label 预替换测试 ====================

    @Test
    @DisplayName("translate - 中英混合 label（订单ID）预替换为英文字段名")
    void testTranslateMixedChineseEnglishLabel() {
        log.info("========== 测试：中英混合 label 预替换 ==========");

        // test_order_index: 订单ID → order_id
        QueryCondition result = expressionService.translate(
                "订单ID = 'xxx'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("order_id", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("xxx", result.getValue());

        log.info("✓ 中英混合 label 预替换测试通过");
    }

    @Test
    @DisplayName("translate - 中英混合 label + 纯中文 label 组合")
    void testTranslateMixedAndPureChineseLabels() {
        log.info("========== 测试：中英混合 + 纯中文 label 组合 ==========");

        // test_nl_user_index 配置了 orderId→订单ID 和 status→状态
        // 订单ID（混合）AND 状态（纯中文）
        QueryCondition result = expressionService.translate(
                "订单ID = 'xxx' AND 状态 = '已完成'", "test_nl_user_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("orderId", left.getField());
        assertEquals("status", right.getField());

        log.info("✓ 中英混合 + 纯中文 label 组合测试通过");
    }

    @Test
    @DisplayName("translate - 多个中英混合 label 组合")
    void testTranslateMultipleMixedLabels() {
        log.info("========== 测试：多个中英混合 label 组合 ==========");

        // 订单ID = 'xxx' AND 订单号 = 'yyy'
        QueryCondition result = expressionService.translate(
                "订单ID = 'xxx' AND 订单号 = 'yyy'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("order_id", left.getField());
        assertEquals("order_id", right.getField());

        log.info("✓ 多个中英混合 label 组合测试通过");
    }

    @Test
    @DisplayName("translate - 同一字段多个 label 映射（订单号/订单ID → order_id）")
    void testTranslateSameFieldMultipleLabels() {
        log.info("========== 测试：同一字段多个 label 映射 ==========");

        // 订单号 → order_id
        QueryCondition r1 = expressionService.translate(
                "订单号 = 'xxx'", "test_order_index");
        log.info("订单号: {}", r1);
        assertEquals("order_id", r1.getField());

        // 订单ID → order_id
        QueryCondition r2 = expressionService.translate(
                "订单ID = 'xxx'", "test_order_index");
        log.info("订单ID: {}", r2);
        assertEquals("order_id", r2.getField());

        // 两个 label 混用在同一表达式中
        QueryCondition r3 = expressionService.translate(
                "订单号 = 'xxx' AND 订单ID = 'yyy'", "test_order_index");
        log.info("混用: {}", r3);
        assertEquals("and", r3.getLogic());

        log.info("✓ 同一字段多个 label 映射测试通过");
    }

    @Test
    @DisplayName("translate - 同一 label 在表达式中多次出现")
    void testTranslateLabelAppearsMultipleTimes() {
        log.info("========== 测试：同一 label 多次出现 ==========");

        // 订单ID 出现两次，两处都被替换
        QueryCondition result = expressionService.translate(
                "订单ID = 'xxx' AND 订单ID = 'yyy'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("order_id", left.getField());
        assertEquals("order_id", right.getField());

        log.info("✓ 同一 label 多次出现测试通过");
    }

    @Test
    @DisplayName("translate - 纯中文 label 不受影响")
    void testTranslatePureChineseLabelUnaffected() {
        log.info("========== 测试：纯中文 label 不受影响 ==========");

        // 状态 = '已完成' → status = '已完成'
        QueryCondition result = expressionService.translate(
                "状态 = '已完成'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("status", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("已完成", result.getValue());

        log.info("✓ 纯中文 label 不受影响测试通过");
    }

    @Test
    @DisplayName("translate - 纯英文字段名不替换（不在 labelMap 中）")
    void testTranslatePureEnglishFieldNoReplace() {
        log.info("========== 测试：纯英文字段名不替换 ==========");

        // order_id = 'xxx' → order_id = 'xxx'（不在 labelMap 中，不替换）
        QueryCondition result = expressionService.translate(
                "order_id = 'xxx'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("order_id", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("xxx", result.getValue());

        log.info("✓ 纯英文字段名不替换测试通过");
    }

    @Test
    @DisplayName("translate - 长短 label 同时存在时长 label 优先（订单ID vs 订单）")
    void testTranslateLabelPriority() {
        log.info("========== 测试：长短 label 优先级 ==========");

        // test_employee 配置了 员工ID → emp_id，姓名 → emp_name
        // 员工ID 是长 label（3字符），工号 → emp_id（2个label）
        // 替换后 visitor 用 reverse mapping 反查英文字段名
        QueryCondition result = expressionService.translate(
                "工号 = 'E001'", "test_employee");
        log.info("result: {}", result);
        assertEquals("emp_id", result.getField());

        log.info("✓ 长短 label 优先级测试通过");
    }

    @Test
    @DisplayName("translate - label 不在 fieldMapping 中则不替换")
    void testTranslateLabelNotInMapping() {
        log.info("========== 测试：label 不在 fieldMapping 中 ==========");

        // "未知标签" 不在 test_order_index 的 field-mapping 中，原样传递
        QueryCondition result = expressionService.translate(
                "未知标签 = 'xxx'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("未知标签", result.getField());

        log.info("✓ label 不在 fieldMapping 中测试通过");
    }

    @Test
    @DisplayName("translate - 无 field-mapping 的索引不替换")
    void testTranslateNoFieldMapping() {
        log.info("========== 测试：无 field-mapping 索引 ==========");

        // test_log_* 无 field-mapping，原样传递
        QueryCondition result = expressionService.translate(
                "城市 = '北京'", "test_log_*");
        log.info("result: {}", result);
        assertEquals("城市", result.getField());

        log.info("✓ 无 field-mapping 索引测试通过");
    }

    @Test
    @DisplayName("translate - STRING 值中的 label 不会被替换")
    void testTranslateStringValueNotReplaced() {
        log.info("========== 测试：STRING 值中 label 不被替换 ==========");

        // 订单号 = 'hello' → order_id = 'hello'
        // STRING 值 'hello' 不是任何 label，不会被 replace 误伤
        QueryCondition result = expressionService.translate(
                "订单号 = 'hello'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("order_id", result.getField());
        assertEquals("hello", result.getValue());

        log.info("✓ STRING 值中 label 不被替换测试通过");
    }

    @Test
    @DisplayName("translate - 中文比较运算符（等于/大于）在 label 替换后仍正常解析")
    void testTranslateChineseOperatorAfterLabelReplace() {
        log.info("========== 测试：中文比较运算符在 label 替换后仍正常 ==========");

        // 订单ID 等于 'xxx' → orderId 等于 'xxx' → orderId = 'xxx'
        QueryCondition result = expressionService.translate(
                "订单ID 等于 'xxx'", "test_nl_user_index");
        log.info("result: {}", result);
        assertEquals("orderId", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("xxx", result.getValue());

        // 城市 大于 '上海' → city 大于 '上海' → city > '上海'
        QueryCondition result2 = expressionService.translate(
                "城市 大于 '上海'", "test_nl_user_index");
        log.info("result2: {}", result2);
        assertEquals("city", result2.getField());
        assertEquals("gt", result2.getOp());

        log.info("✓ 中文比较运算符正常解析测试通过");
    }

    @Test
    @DisplayName("translate - 中文逻辑运算符（且/或/非）在 label 替换后仍正常解析")
    void testTranslateChineseLogicalOperatorAfterLabelReplace() {
        log.info("========== 测试：中文逻辑运算符在 label 替换后仍正常 ==========");

        // 订单ID='xxx' 且 状态='已完成' → orderId='xxx' 且 status='已完成'
        QueryCondition result = expressionService.translate(
                "订单ID='xxx' 且 状态='已完成'", "test_nl_user_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("orderId", left.getField());
        assertEquals("status", right.getField());

        log.info("✓ 中文逻辑运算符正常解析测试通过");
    }

    @Test
    @DisplayName("translate - 复杂表达式（中英混合 label + 纯中文 label + 中文运算符）")
    void testTranslateComplexExpressionMixedLabels() {
        log.info("========== 测试：复杂表达式混合场景 ==========");

        // 订单ID='xxx' 且 城市='北京'
        // 解析为 (订单ID='xxx' 且 城市='北京')，顶层 getConditions()=2
        QueryCondition result = expressionService.translate(
                "订单ID='xxx' 且 城市='北京'", "test_nl_user_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("orderId", left.getField());
        assertEquals("city", right.getField());

        log.info("✓ 复杂表达式混合场景测试通过");
    }

    // ==================== v1.6.5 DSL 扁平化测试 ====================

    @Test
    @DisplayName("translate - 三个 AND 条件生成扁平 QueryCondition")
    void testThreeAndExpressionFlatten() {
        log.info("========== 测试：三个 AND 条件扁平 ==========");

        QueryCondition result = expressionService.translate(
                "name = 'Alice' AND age >= 18 AND points <= 1000",
                "test_nl_user_index");
        log.info("result: {}", result);

        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size(),
                "A AND B AND C → left-associative: AND(AND(A, B), C)");
        // 内层 AND(A, B) 在 get(0)，有 2 个叶子条件
        QueryCondition innerAnd = result.getConditions().get(0);
        assertEquals(2, innerAnd.getConditions().size(),
                "内层 AND(A, B) 有 2 个条件");

        log.info("✓ 三个 AND 条件扁平测试通过");
    }

    @Test
    @DisplayName("translate - 三个 OR 条件生成扁平 QueryCondition")
    void testThreeOrExpressionFlatten() {
        log.info("========== 测试：三个 OR 条件扁平 ==========");

        QueryCondition result = expressionService.translate(
                "name = 'Alice' OR name = 'Bob' OR name = 'Carol'",
                "test_nl_user_index");
        log.info("result: {}", result);

        assertEquals("or", result.getLogic());
        assertEquals(2, result.getConditions().size(),
                "A OR B OR C → left-associative: OR(OR(A, B), C)");
        // 内层 OR(A, B) 在 get(0)，有 2 个叶子条件
        QueryCondition innerOr = result.getConditions().get(0);
        assertEquals(2, innerOr.getConditions().size(),
                "内层 OR(A, B) 有 2 个条件");

        log.info("✓ 三个 OR 条件扁平测试通过");
    }

    @Test
    @DisplayName("translate - 时间范围补集生成 OR range DSL")
    void testTimeRangeOutsideDsl() {
        QueryCondition condition = expressionService.translate(
                "NOT (createTime = 最近7天)", "test_nl_user_index");
        IndexMetadata metadata = mappingManager.getMetadata("test_nl_user_index");
        QueryBuilder query = queryDslBuilder.build(metadata, condition);
        log.info("time range outside condition={}, DSL={}", condition, query);

        assertTrue(query instanceof BoolQueryBuilder, "时间范围补集应生成 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) query;
        assertEquals(2, boolQuery.should().size(), "时间范围补集应包含两个 should 范围条件");
        assertEquals("1", boolQuery.minimumShouldMatch(), "时间范围补集应至少命中一个范围条件");
    }

    @Test
    @DisplayName("translate - 三个 AND 条件端到端 DSL 无嵌套")
    void testThreeAndExpressionEndToEnd() {
        log.info("========== 测试：三个 AND 条件端到端 DSL 扁平 ==========");

        // 端到端：expression → QueryCondition → QueryDslBuilder.build() → DSL
        QueryCondition condition = expressionService.translate(
                "name = 'Alice' AND age >= 18 AND points <= 1000",
                "test_nl_user_index");
        IndexMetadata metadata = mappingManager.getMetadata("test_nl_user_index");
        QueryBuilder query = queryDslBuilder.build(metadata, condition);
        String dsl = query.toString();

        log.info("DSL: {}", dsl);

        assertTrue(query instanceof BoolQueryBuilder,
                "结果应为 BoolQueryBuilder");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) query;
        assertEquals(3, boolQuery.must().size(),
                "must 中应有 3 个条件，无嵌套");
        assertEquals(0, boolQuery.should().size(),
                "should 应为空");

        log.info("✓ 三个 AND 条件端到端测试通过");
    }
}
