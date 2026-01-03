package io.github.surezzzzzz.sdk.naturallanguage.parser.test.cases;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.LogicType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AggregationIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AnalyticsIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.Intent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.NLParser;
import io.github.surezzzzzz.sdk.naturallanguage.parser.test.NaturalLanguageParserTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聚合解析器测试（增强版）
 * <p>
 * 测试范围：
 * - 简单桶聚合（TERMS）
 * - 桶聚合 + 参数（size）
 * - 嵌套聚合（桶 + 指标）
 * - 时间聚合（DATE_HISTOGRAM）
 * - 多个并行聚合
 * - 复杂综合场景
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = NaturalLanguageParserTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AggregationParserTest {

    @Autowired
    private NLParser nlParser;

    // ==================== 辅助方法 ====================

    private AnalyticsIntent asAnalyticsIntent(Intent intent) {
        assertTrue(intent instanceof AnalyticsIntent, "Intent应该是AnalyticsIntent类型");
        return (AnalyticsIntent) intent;
    }

    @BeforeAll
    static void setupAll() {
        log.info("========== 开始聚合解析器增强测试 ==========");
    }

    @AfterAll
    static void cleanupAll() {
        log.info("========== 聚合解析器增强测试完成 ==========");
    }

    // ==================== Phase 1: 基础桶聚合（TERMS） ====================

    @Test
    @Order(1)
    @DisplayName("Phase 1.1 - 简单桶聚合：按城市分组")
    void testSimpleBucketAggregation() {
        log.info("========== 测试：简单桶聚合 ==========");

        String query = "按城市分组";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        assertTrue(intent.hasAggregation(), "应该包含聚合");
        assertEquals(1, intent.getAggregations().size(), "应该有1个聚合");

        AggregationIntent agg = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, agg.getType(), "聚合类型应该是TERMS");
        assertEquals("城市", agg.getGroupByFieldHint(), "分组字段应该是'城市'");
        assertNotNull(agg.getName(), "聚合名称不应为null");

        log.info("✓ 简单桶聚合测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("Phase 1.2 - 桶聚合 + size参数：按城市分组前10个")
    void testBucketAggregationWithSize() {
        log.info("========== 测试：桶聚合 + size参数 ==========");

        String query = "按城市分组前10个";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        assertTrue(intent.hasAggregation(), "应该包含聚合");
        AggregationIntent agg = intent.getAggregations().get(0);

        assertEquals(AggType.TERMS, agg.getType(), "聚合类型应该是TERMS");
        assertEquals("城市", agg.getGroupByFieldHint(), "分组字段应该是'城市'");
        assertEquals(10, agg.getSize(), "size应该是10");

        log.info("✓ 桶聚合 + size参数测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("Phase 1.3 - size参数变体：限制5个、最多20个")
    void testSizeParameterVariants() {
        log.info("========== 测试：size参数变体 ==========");

        // 变体1: 限制5个
        String query1 = "按城市分组限制5个";
        log.info("查询1: {}", query1);
        AnalyticsIntent intent1 = asAnalyticsIntent(nlParser.parse(query1));
        assertEquals(5, intent1.getAggregations().get(0).getSize(), "size应该是5");
        log.info("✓ '限制5个' 解析正确");

        // 变体2: 最多20个
        String query2 = "按城市分组最多20个";
        log.info("查询2: {}", query2);
        AnalyticsIntent intent2 = asAnalyticsIntent(nlParser.parse(query2));
        assertEquals(20, intent2.getAggregations().get(0).getSize(), "size应该是20");
        log.info("✓ '最多20个' 解析正确");

        // 变体3: 取前15个
        String query3 = "按城市分组取前15个";
        log.info("查询3: {}", query3);
        AnalyticsIntent intent3 = asAnalyticsIntent(nlParser.parse(query3));
        assertEquals(15, intent3.getAggregations().get(0).getSize(), "size应该是15");
        log.info("✓ '取前15个' 解析正确");

        log.info("✓ size参数变体测试全部通过");
    }

    @Test
    @Order(4)
    @DisplayName("Phase 1.4 - 英文字段：按city分组")
    void testEnglishField() {
        log.info("========== 测试：英文字段 ==========");

        String query = "按city分组前10个";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        AggregationIntent agg = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, agg.getType(), "聚合类型应该是TERMS");
        assertEquals("city", agg.getGroupByFieldHint(), "分组字段应该是'city'");
        assertEquals(10, agg.getSize(), "size应该是10");

        log.info("✓ 英文字段测试通过");
    }

    // ==================== Phase 2: 嵌套聚合 ====================

    @Test
    @Order(10)
    @DisplayName("Phase 2.1 - 嵌套聚合：按城市分组统计平均年龄")
    void testNestedAggregation() {
        log.info("========== 测试：嵌套聚合 ==========");

        String query = "按城市分组统计平均年龄";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        assertTrue(intent.hasAggregation(), "应该包含聚合");
        assertEquals(1, intent.getAggregations().size(), "应该有1个顶层聚合");

        // 验证桶聚合
        AggregationIntent bucketAgg = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, bucketAgg.getType(), "顶层聚合类型应该是TERMS");
        assertEquals("城市", bucketAgg.getGroupByFieldHint(), "分组字段应该是'城市'");

        // 验证嵌套的指标聚合
        assertNotNull(bucketAgg.getChildren(), "应该有嵌套聚合");
        assertEquals(1, bucketAgg.getChildren().size(), "应该有1个嵌套聚合");

        AggregationIntent metricAgg = bucketAgg.getChildren().get(0);
        assertEquals(AggType.AVG, metricAgg.getType(), "嵌套聚合类型应该是AVG");
        assertEquals("年龄", metricAgg.getFieldHint(), "聚合字段应该是'年龄'");

        log.info("✓ 嵌套聚合测试通过");
    }

    @Test
    @Order(11)
    @DisplayName("Phase 2.2 - 嵌套聚合 + size：按城市分组前10个统计平均年龄")
    void testNestedAggregationWithSize() {
        log.info("========== 测试：嵌套聚合 + size ==========");

        String query = "按城市分组前10个统计平均年龄";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        AggregationIntent bucketAgg = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, bucketAgg.getType(), "顶层聚合类型应该是TERMS");
        assertEquals("城市", bucketAgg.getGroupByFieldHint(), "分组字段应该是'城市'");
        assertEquals(10, bucketAgg.getSize(), "size应该是10");

        // 验证嵌套聚合
        assertEquals(1, bucketAgg.getChildren().size(), "应该有1个嵌套聚合");
        AggregationIntent metricAgg = bucketAgg.getChildren().get(0);
        assertEquals(AggType.AVG, metricAgg.getType(), "嵌套聚合类型应该是AVG");
        assertEquals("年龄", metricAgg.getFieldHint(), "聚合字段应该是'年龄'");

        log.info("✓ 嵌套聚合 + size测试通过");
    }

    @Test
    @Order(12)
    @DisplayName("Phase 2.3 - 多种指标聚合：求和、最大、最小")
    void testDifferentMetricAggregations() {
        log.info("========== 测试：多种指标聚合 ==========");

        // SUM
        String query1 = "按城市分组求和金额";
        log.info("查询1: {}", query1);
        AnalyticsIntent intent1 = asAnalyticsIntent(nlParser.parse(query1));
        AggregationIntent nested1 = intent1.getAggregations().get(0).getChildren().get(0);
        assertEquals(AggType.SUM, nested1.getType(), "应该是SUM");
        assertEquals("金额", nested1.getFieldHint(), "字段应该是'金额'");
        log.info("✓ SUM聚合解析正确");

        // MAX
        String query2 = "按城市分组最大年龄";
        log.info("查询2: {}", query2);
        AnalyticsIntent intent2 = asAnalyticsIntent(nlParser.parse(query2));
        AggregationIntent nested2 = intent2.getAggregations().get(0).getChildren().get(0);
        assertEquals(AggType.MAX, nested2.getType(), "应该是MAX");
        assertEquals("年龄", nested2.getFieldHint(), "字段应该是'年龄'");
        log.info("✓ MAX聚合解析正确");

        // MIN
        String query3 = "按城市分组最小年龄";
        log.info("查询3: {}", query3);
        AnalyticsIntent intent3 = asAnalyticsIntent(nlParser.parse(query3));
        AggregationIntent nested3 = intent3.getAggregations().get(0).getChildren().get(0);
        assertEquals(AggType.MIN, nested3.getType(), "应该是MIN");
        assertEquals("年龄", nested3.getFieldHint(), "字段应该是'年龄'");
        log.info("✓ MIN聚合解析正确");

        log.info("✓ 多种指标聚合测试全部通过");
    }

    // ==================== Phase 3: 时间聚合（DATE_HISTOGRAM） ====================

    @Test
    @Order(20)
    @DisplayName("Phase 3.1 - 时间聚合：按创建时间每天统计")
    void testDateHistogramAggregation() {
        log.info("========== 测试：时间聚合 ==========");

        String query = "按创建时间每天统计";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        assertTrue(intent.hasAggregation(), "应该包含聚合");
        AggregationIntent agg = intent.getAggregations().get(0);

        assertEquals(AggType.DATE_HISTOGRAM, agg.getType(), "聚合类型应该是DATE_HISTOGRAM");
        assertEquals("创建时间", agg.getFieldHint(), "字段应该是'创建时间'");
        assertEquals("1d", agg.getInterval(), "interval应该是'1d'");

        log.info("✓ 时间聚合测试通过");
    }

    @Test
    @Order(21)
    @DisplayName("Phase 3.2 - 时间间隔变体：每小时、每周、每月")
    void testDateHistogramIntervalVariants() {
        log.info("========== 测试：时间间隔变体 ==========");

        // 每小时
        String query1 = "按创建时间每小时统计";
        log.info("查询1: {}", query1);
        AnalyticsIntent intent1 = asAnalyticsIntent(nlParser.parse(query1));
        AggregationIntent agg1 = intent1.getAggregations().get(0);
        assertEquals(AggType.DATE_HISTOGRAM, agg1.getType(), "应该是DATE_HISTOGRAM");
        assertEquals("1h", agg1.getInterval(), "interval应该是'1h'");
        log.info("✓ '每小时' 解析正确");

        // 每周
        String query2 = "按创建时间每周统计";
        log.info("查询2: {}", query2);
        AnalyticsIntent intent2 = asAnalyticsIntent(nlParser.parse(query2));
        AggregationIntent agg2 = intent2.getAggregations().get(0);
        assertEquals(AggType.DATE_HISTOGRAM, agg2.getType(), "应该是DATE_HISTOGRAM");
        assertEquals("1w", agg2.getInterval(), "interval应该是'1w'");
        log.info("✓ '每周' 解析正确");

        // 每月
        String query3 = "按创建时间每月统计";
        log.info("查询3: {}", query3);
        AnalyticsIntent intent3 = asAnalyticsIntent(nlParser.parse(query3));
        AggregationIntent agg3 = intent3.getAggregations().get(0);
        assertEquals(AggType.DATE_HISTOGRAM, agg3.getType(), "应该是DATE_HISTOGRAM");
        assertEquals("1M", agg3.getInterval(), "interval应该是'1M'");
        log.info("✓ '每月' 解析正确");

        log.info("✓ 时间间隔变体测试全部通过");
    }

    @Test
    @Order(22)
    @DisplayName("Phase 3.3 - 简化时间聚合：每天统计（无字段名）")
    void testSimpleDateHistogram() {
        log.info("========== 测试：简化时间聚合 ==========");

        String query = "每天统计";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        AggregationIntent agg = intent.getAggregations().get(0);
        assertEquals(AggType.DATE_HISTOGRAM, agg.getType(), "聚合类型应该是DATE_HISTOGRAM");
        assertEquals("1d", agg.getInterval(), "interval应该是'1d'");
        // fieldHint可能为null或空，因为没有明确指定时间字段

        log.info("✓ 简化时间聚合测试通过");
    }

    // ==================== Phase 4: 多个并行聚合 ====================

    @Test
    @Order(30)
    @DisplayName("Phase 4.1 - 多个并行聚合：按城市分组，同时按时间每天统计")
    void testMultipleParallelAggregations() {
        log.info("========== 测试：多个并行聚合 ==========");

        String query = "按城市分组，同时按创建时间每天统计";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        assertTrue(intent.hasAggregation(), "应该包含聚合");
        assertEquals(2, intent.getAggregations().size(), "应该有2个并行聚合");

        // 验证第一个聚合（TERMS）
        AggregationIntent agg1 = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, agg1.getType(), "第一个聚合应该是TERMS");
        assertEquals("城市", agg1.getGroupByFieldHint(), "分组字段应该是'城市'");

        // 验证第二个聚合（DATE_HISTOGRAM）
        AggregationIntent agg2 = intent.getAggregations().get(1);
        assertEquals(AggType.DATE_HISTOGRAM, agg2.getType(), "第二个聚合应该是DATE_HISTOGRAM");
        assertEquals("创建时间", agg2.getFieldHint(), "字段应该是'创建时间'");
        assertEquals("1d", agg2.getInterval(), "interval应该是'1d'");

        log.info("✓ 多个并行聚合测试通过");
    }

    @Test
    @Order(31)
    @DisplayName("Phase 4.2 - 并行聚合 + 嵌套：按城市分组统计平均年龄，同时按时间每天统计")
    void testParallelAggregationsWithNesting() {
        log.info("========== 测试：并行聚合 + 嵌套 ==========");

        String query = "按城市分组统计平均年龄，同时按创建时间每天统计";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        assertEquals(2, intent.getAggregations().size(), "应该有2个并行聚合");

        // 验证第一个聚合（TERMS + nested AVG）
        AggregationIntent agg1 = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, agg1.getType(), "第一个聚合应该是TERMS");
        assertEquals("城市", agg1.getGroupByFieldHint(), "分组字段应该是'城市'");
        assertEquals(1, agg1.getChildren().size(), "应该有1个嵌套聚合");
        assertEquals(AggType.AVG, agg1.getChildren().get(0).getType(), "嵌套聚合应该是AVG");
        assertEquals("年龄", agg1.getChildren().get(0).getFieldHint(), "聚合字段应该是'年龄'");

        // 验证第二个聚合（DATE_HISTOGRAM）
        AggregationIntent agg2 = intent.getAggregations().get(1);
        assertEquals(AggType.DATE_HISTOGRAM, agg2.getType(), "第二个聚合应该是DATE_HISTOGRAM");
        assertEquals("创建时间", agg2.getFieldHint(), "字段应该是'创建时间'");
        assertEquals("1d", agg2.getInterval(), "interval应该是'1d'");

        log.info("✓ 并行聚合 + 嵌套测试通过");
    }

    @Test
    @Order(32)
    @DisplayName("Phase 4.3 - 多种分隔词：并且、还有、以及")
    void testDifferentSeparators() {
        log.info("========== 测试：多种分隔词 ==========");

        // 并且
        String query1 = "按城市分组并且按时间每天统计";
        log.info("查询1: {}", query1);
        AnalyticsIntent intent1 = asAnalyticsIntent(nlParser.parse(query1));
        assertEquals(2, intent1.getAggregations().size(), "'并且' 应该分隔出2个聚合");
        log.info("✓ '并且' 分隔正确");

        // 还有
        String query2 = "按城市分组还有按时间每天统计";
        log.info("查询2: {}", query2);
        AnalyticsIntent intent2 = asAnalyticsIntent(nlParser.parse(query2));
        assertEquals(2, intent2.getAggregations().size(), "'还有' 应该分隔出2个聚合");
        log.info("✓ '还有' 分隔正确");

        // 以及
        String query3 = "按城市分组以及按时间每天统计";
        log.info("查询3: {}", query3);
        AnalyticsIntent intent3 = asAnalyticsIntent(nlParser.parse(query3));
        assertEquals(2, intent3.getAggregations().size(), "'以及' 应该分隔出2个聚合");
        log.info("✓ '以及' 分隔正确");

        log.info("✓ 多种分隔词测试全部通过");
    }

    // ==================== 复杂综合场景 ====================

    @Test
    @Order(40)
    @DisplayName("综合场景1 - 复杂嵌套 + 参数：按城市分组前10个统计平均年龄，同时按时间每天统计")
    void testComplexScenario1() {
        log.info("========== 测试：综合场景1 - 复杂嵌套 + 参数 ==========");

        String query = "按城市分组前10个统计平均年龄，同时按创建时间每天统计";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        assertEquals(2, intent.getAggregations().size(), "应该有2个并行聚合");

        // 验证第一个聚合（TERMS + size + nested AVG）
        AggregationIntent agg1 = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, agg1.getType(), "第一个聚合应该是TERMS");
        assertEquals("城市", agg1.getGroupByFieldHint(), "分组字段应该是'城市'");
        assertEquals(10, agg1.getSize(), "size应该是10");
        assertEquals(1, agg1.getChildren().size(), "应该有1个嵌套聚合");
        assertEquals(AggType.AVG, agg1.getChildren().get(0).getType(), "嵌套聚合应该是AVG");

        // 验证第二个聚合（DATE_HISTOGRAM）
        AggregationIntent agg2 = intent.getAggregations().get(1);
        assertEquals(AggType.DATE_HISTOGRAM, agg2.getType(), "第二个聚合应该是DATE_HISTOGRAM");
        assertEquals("1d", agg2.getInterval(), "interval应该是'1d'");

        log.info("✓ 综合场景1测试通过");
    }

    @Test
    @Order(41)
    @DisplayName("综合场景2 - 匹配API 2聚合部分")
    void testComplexScenario2_MatchingAPI2() {
        log.info("========== 测试：综合场景2 - 匹配API 2聚合部分 ==========");

        // 这个查询应该生成与API 2相同的聚合结构
        String query = "查询user_behavior索引，年龄大于等于18并且城市在北京、上海，" +
                "按城市分组前10个统计平均年龄，同时按创建时间每天统计";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        // 验证索引
        assertEquals("user_behavior", intent.getIndexHint(), "索引应该是'user_behavior'");

        // 验证条件
        assertTrue(intent.hasCondition(), "应该包含条件");
        assertEquals(LogicType.AND, intent.getCondition().getLogic(), "逻辑应该是AND");

        // 验证聚合
        assertTrue(intent.hasAggregation(), "应该包含聚合");
        assertEquals(2, intent.getAggregations().size(), "应该有2个并行聚合");

        // 验证第一个聚合（city_distribution）
        AggregationIntent cityAgg = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, cityAgg.getType(), "应该是TERMS");
        assertEquals("城市", cityAgg.getGroupByFieldHint(), "字段应该是'城市'");
        assertEquals(10, cityAgg.getSize(), "size应该是10");
        assertEquals(1, cityAgg.getChildren().size(), "应该有1个嵌套聚合");
        assertEquals(AggType.AVG, cityAgg.getChildren().get(0).getType(), "嵌套聚合应该是AVG");
        assertEquals("年龄", cityAgg.getChildren().get(0).getFieldHint(), "聚合字段应该是'年龄'");

        // 验证第二个聚合（daily_stats）
        AggregationIntent dailyAgg = intent.getAggregations().get(1);
        assertEquals(AggType.DATE_HISTOGRAM, dailyAgg.getType(), "应该是DATE_HISTOGRAM");
        assertEquals("创建时间", dailyAgg.getFieldHint(), "字段应该是'创建时间'");
        assertEquals("1d", dailyAgg.getInterval(), "interval应该是'1d'");

        log.info("✓ 综合场景2测试通过 - 成功匹配API 2聚合结构");
    }

    @Test
    @Order(42)
    @DisplayName("综合场景3 - 纯聚合（无条件）")
    void testPureAggregation() {
        log.info("========== 测试：综合场景3 - 纯聚合（无条件） ==========");

        String query = "统计平均年龄";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        assertTrue(intent.hasAggregation(), "应该包含聚合");
        assertEquals(1, intent.getAggregations().size(), "应该有1个聚合");

        AggregationIntent agg = intent.getAggregations().get(0);
        assertEquals(AggType.AVG, agg.getType(), "聚合类型应该是AVG");
        assertEquals("年龄", agg.getFieldHint(), "聚合字段应该是'年龄'");

        log.info("✓ 纯聚合测试通过");
    }

    @Test
    @Order(50)
    @DisplayName("终极测试 - 所有聚合功能综合")
    void testUltimateAggregation() {
        log.info("========== 终极测试 - 所有聚合功能综合 ==========");

        String query = "查询user_behavior索引，" +
                "年龄大于等于18并且年龄小于等于60，" +
                "城市在北京、上海、深圳，" +
                "按城市分组前10个统计平均年龄，" +
                "同时按创建时间每天统计";
        log.info("查询: {}", query);

        AnalyticsIntent intent = asAnalyticsIntent(nlParser.parse(query));
        log.info("解析结果: {}", intent);

        // 验证所有组件
        assertEquals("user_behavior", intent.getIndexHint(), "索引正确");
        assertTrue(intent.hasCondition(), "包含条件");
        assertTrue(intent.hasAggregation(), "包含聚合");

        // 验证聚合
        assertEquals(2, intent.getAggregations().size(), "2个并行聚合");

        // 第一个聚合：城市分组 + 嵌套平均年龄
        AggregationIntent cityAgg = intent.getAggregations().get(0);
        assertEquals(AggType.TERMS, cityAgg.getType(), "TERMS聚合");
        assertEquals("城市", cityAgg.getGroupByFieldHint(), "分组字段是'城市'");
        assertEquals(10, cityAgg.getSize(), "size=10");
        assertEquals(1, cityAgg.getChildren().size(), "有1个嵌套聚合");
        assertEquals(AggType.AVG, cityAgg.getChildren().get(0).getType(), "嵌套聚合是AVG");
        assertEquals("年龄", cityAgg.getChildren().get(0).getFieldHint(), "聚合字段是'年龄'");

        // 第二个聚合：时间聚合
        AggregationIntent dailyAgg = intent.getAggregations().get(1);
        assertEquals(AggType.DATE_HISTOGRAM, dailyAgg.getType(), "DATE_HISTOGRAM聚合");
        assertEquals("创建时间", dailyAgg.getFieldHint(), "字段是'创建时间'");
        assertEquals("1d", dailyAgg.getInterval(), "interval=1d");

        log.info("✓ 终极测试通过 - 所有聚合功能正常工作");
        log.info("========== ✓ 聚合解析器增强测试全部通过 ==========");
    }
}
