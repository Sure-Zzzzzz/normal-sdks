package io.github.surezzzzzz.sdk.naturallanguage.parser.test.cases;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AggregationIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AnalyticsIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.Intent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.NLParser;
import io.github.surezzzzzz.sdk.naturallanguage.parser.test.NaturalLanguageParserTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聚合增强测试 - 核心测试
 * 验证能否生成API 2的完整DSL结构
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = NaturalLanguageParserTestApplication.class)
class AggregationEnhancementTest {

    @Autowired
    private NLParser nlParser;

    @Test
    void testAPI2CompleteAggregation() {
        log.info("========== 测试：API 2完整聚合结构 ==========");

        // 目标DSL:
        // {
        //   "aggs": [
        //     {
        //       "name": "city_distribution",
        //       "type": "terms",
        //       "field": "city",
        //       "size": 10,
        //       "aggs": [
        //         {
        //           "name": "avg_age",
        //           "type": "avg",
        //           "field": "age"
        //         }
        //       ]
        //     },
        //     {
        //       "name": "daily_stats",
        //       "type": "date_histogram",
        //       "field": "createTime",
        //       "interval": "1d"
        //     }
        //   ]
        // }

        String query = "查询user_behavior索引，status等于active，按城市分组前10个统计平均年龄，同时按创建时间每天统计";
        log.info("查询: {}", query);

        Intent intent = nlParser.parse(query);
        log.info("Intent类型: {}", intent.getClass().getSimpleName());
        log.info("解析结果: {}", intent);

        // 验证类型
        assertTrue(intent instanceof AnalyticsIntent, "应该是AnalyticsIntent");
        AnalyticsIntent analyticsIntent = (AnalyticsIntent) intent;

        // 验证索引
        assertEquals("user_behavior", analyticsIntent.getIndexHint(), "索引应该是'user_behavior'");

        // 验证条件
        assertTrue(analyticsIntent.hasCondition(), "应该包含查询条件");
        assertNotNull(analyticsIntent.getCondition(), "条件不应为null");

        // 验证聚合数量
        assertTrue(analyticsIntent.hasAggregation(), "应该包含聚合");
        assertEquals(2, analyticsIntent.getAggregations().size(), "应该有2个并行聚合");

        // 验证第一个聚合：city_distribution (TERMS + size + nested AVG)
        AggregationIntent cityAgg = analyticsIntent.getAggregations().get(0);
        log.info("第一个聚合: type={}, groupByField={}, size={}, children={}",
                cityAgg.getType(), cityAgg.getGroupByFieldHint(), cityAgg.getSize(),
                cityAgg.getChildren().size());

        assertEquals(AggType.TERMS, cityAgg.getType(), "第一个聚合类型应该是TERMS");
        assertEquals("城市", cityAgg.getGroupByFieldHint(), "分组字段应该是'城市'");
        assertEquals(10, cityAgg.getSize(), "size应该是10");
        assertNotNull(cityAgg.getName(), "应该有聚合名称");

        // 验证嵌套的AVG聚合
        assertEquals(1, cityAgg.getChildren().size(), "应该有1个嵌套聚合");
        AggregationIntent avgAgg = cityAgg.getChildren().get(0);
        log.info("嵌套聚合: type={}, field={}", avgAgg.getType(), avgAgg.getFieldHint());

        assertEquals(AggType.AVG, avgAgg.getType(), "嵌套聚合类型应该是AVG");
        assertEquals("年龄", avgAgg.getFieldHint(), "聚合字段应该是'年龄'");
        assertNotNull(avgAgg.getName(), "嵌套聚合应该有名称");

        // 验证第二个聚合：daily_stats (DATE_HISTOGRAM + interval)
        AggregationIntent dailyAgg = analyticsIntent.getAggregations().get(1);
        log.info("第二个聚合: type={}, field={}, interval={}",
                dailyAgg.getType(), dailyAgg.getFieldHint(), dailyAgg.getInterval());

        assertEquals(AggType.DATE_HISTOGRAM, dailyAgg.getType(), "第二个聚合类型应该是DATE_HISTOGRAM");
        assertEquals("创建时间", dailyAgg.getFieldHint(), "字段应该是'创建时间'");
        assertEquals("1d", dailyAgg.getInterval(), "interval应该是'1d'");
        assertNotNull(dailyAgg.getName(), "应该有聚合名称");

        log.info("========== ✓ API 2完整聚合结构测试通过 ==========");
        log.info("成功生成的DSL结构:");
        log.info("  [0] TERMS(city, size=10) → nested: AVG(age)");
        log.info("  [1] DATE_HISTOGRAM(createTime, interval=1d)");
    }
}
