package io.github.surezzzzzz.sdk.naturallanguage.parser.test.cases;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.LogicType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.OperatorType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.SortOrder;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AnalyticsIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.ConditionIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.Intent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.QueryIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.NLParser;
import io.github.surezzzzzz.sdk.naturallanguage.parser.test.NaturalLanguageParserTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自然语言解析器端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = NaturalLanguageParserTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NLParserEndToEndTest {

    @Autowired
    private NLParser nlParser;

    // ==================== 辅助方法 ====================

    private QueryIntent asQueryIntent(Intent intent) {
        assertTrue(intent instanceof QueryIntent, "Intent应该是QueryIntent类型");
        return (QueryIntent) intent;
    }

    private AnalyticsIntent asAnalyticsIntent(Intent intent) {
        assertTrue(intent instanceof AnalyticsIntent, "Intent应该是AnalyticsIntent类型");
        return (AnalyticsIntent) intent;
    }

    @BeforeAll
    static void setupAll() {
        log.info("========== 开始自然语言解析器测试 ==========");
    }

    @AfterAll
    static void cleanupAll() {
        log.info("========== 自然语言解析器测试完成 ==========");
    }

    // ==================== 超级端到端测试 ====================

    @Test
    @Order(1)
    @DisplayName("超级端到端测试 - 全场景覆盖")
    void testSuperEndToEnd() {
        log.info("========== 超级端到端测试 - 全场景覆盖 ==========");

        // 场景1: 复杂查询 - 多条件 + IN + LIKE OR + 排序 + 分页
        log.info("\n【场景1】复杂查询测试");
        String query1 = "年龄大于等于18并且年龄小于60并且城市在北京,上海,深圳并且名字包含张或李按创建时间降序限制10条";
        log.info("查询: {}", query1);
        QueryIntent q1 = asQueryIntent(nlParser.parse(query1));
        log.info("解析结果: {}", q1);
        assertTrue(q1.hasCondition() && q1.hasSort() && q1.hasPagination());
        assertEquals(10, q1.getPagination().getLimit());
        assertEquals(SortOrder.DESC, q1.getSorts().get(0).getOrder());
        log.info("✓ 场景1通过：复杂查询解析正确");

        // 场景2: 聚合查询 - BETWEEN + 聚合
        log.info("\n【场景2】聚合查询测试");
        String query2 = "城市是北京并且年龄介于25,45统计平均年龄";
        log.info("查询: {}", query2);
        AnalyticsIntent a2 = asAnalyticsIntent(nlParser.parse(query2));
        log.info("解析结果: {}", a2);
        assertTrue(a2.hasCondition() && a2.hasAggregation());
        assertEquals(AggType.AVG, a2.getAggregations().get(0).getType());
        assertTrue(findOperatorInCondition(a2.getCondition(), OperatorType.BETWEEN));
        log.info("✓ 场景2通过：聚合查询解析正确");

        // 场景3: 多种分隔符（逗号、中文逗号、顿号）
        log.info("\n【场景3】多种分隔符测试");
        assertEquals(3, asQueryIntent(nlParser.parse("城市在北京,上海,深圳")).getCondition().getValues().size());
        assertEquals(3, asQueryIntent(nlParser.parse("城市在北京，上海，深圳")).getCondition().getValues().size());
        assertEquals(3, asQueryIntent(nlParser.parse("城市在北京、上海、深圳")).getCondition().getValues().size());
        log.info("✓ 场景3通过：多种分隔符解析正确");

        // 场景4: 逗号作为子句分隔符
        log.info("\n【场景4】逗号作为子句分隔符");
        String query4 = "年龄大于18,城市是北京,名字包含张";
        log.info("查询: {}", query4);
        ConditionIntent c4 = asQueryIntent(nlParser.parse(query4)).getCondition();
        log.info("解析结果: {}", c4);
        assertEquals(LogicType.AND, c4.getLogic());
        assertEquals(2, c4.getChildren().size());
        log.info("✓ 场景4通过：逗号作为子句分隔符解析正确");

        // 场景5: LIKE + OR逻辑
        log.info("\n【场景5】LIKE操作符 with OR逻辑");
        String query5 = "名字包含张或李";
        log.info("查询: {}", query5);
        ConditionIntent c5 = asQueryIntent(nlParser.parse(query5)).getCondition();
        log.info("解析结果: {}", c5);
        assertEquals(LogicType.OR, c5.getLogic());
        assertEquals(OperatorType.LIKE, c5.getOperator());
        log.info("✓ 场景5通过：LIKE+OR逻辑解析正确");

        // 场景6: 数值解析（整数、浮点数）
        log.info("\n【场景6】数值解析");
        assertEquals(25L, asQueryIntent(nlParser.parse("年龄等于25")).getCondition().getValue());
        assertEquals(99.99, asQueryIntent(nlParser.parse("价格大于99.99")).getCondition().getValue());
        log.info("✓ 场景6通过：数值解析正确");

        // 场景7: 英文关键词
        log.info("\n【场景7】英文关键词");
        assertEquals(LogicType.AND, asQueryIntent(nlParser.parse("age>18 and city=Beijing")).getCondition().getLogic());
        assertEquals(AggType.AVG, asAnalyticsIntent(nlParser.parse("avg age")).getAggregations().get(0).getType());
        log.info("✓ 场景7通过：英文关键词解析正确");

        // 场景8: 自然语言查询（口语化） - 停用词过滤 + 隐式AND + 顿号分隔
        log.info("\n【场景8】自然语言查询（口语化）");
        String query8 = "帮我查一下年龄大于等于18小于60，城市在北京、上海、深圳并且名字包含张或李，按创建时间降序，返回前10条数据就行";
        log.info("查询: {}", query8);
        QueryIntent q8 = asQueryIntent(nlParser.parse(query8));
        log.info("解析结果: {}", q8);
        assertTrue(q8.hasCondition() && q8.hasSort() && q8.hasPagination());
        assertTrue(findValueInCondition(q8.getCondition(), 18L) && findValueInCondition(q8.getCondition(), 60L));
        assertTrue(findOperatorInCondition(q8.getCondition(), OperatorType.IN));
        assertEquals(10, q8.getPagination().getLimit());
        log.info("✓ 场景8通过：自然语言查询（口语化）解析正确");

        // 场景9: 索引/表名提取
        log.info("\n【场景9】索引/表名提取");
        String query9 = "帮我查一下user这个索引，年龄大于等于18小于60，城市在北京、上海、深圳并且名字包含张或李，按创建时间降序，返回前10条数据就行";
        log.info("查询: {}", query9);
        QueryIntent q9 = asQueryIntent(nlParser.parse(query9));
        log.info("解析结果: {}", q9);
        assertEquals("user", q9.getIndexHint());
        assertTrue(q9.hasCondition() && q9.hasSort() && q9.hasPagination());
        assertTrue(findValueInCondition(q9.getCondition(), 18L) && findValueInCondition(q9.getCondition(), 60L));
        log.info("✓ 场景9通过：索引/表名提取正确");

        // 场景10: 复杂分页 - offset + limit
        log.info("\n【场景10】复杂分页 - offset + limit");
        String query10 = "年龄大于18，跳过20条，返回10条";
        log.info("查询: {}", query10);
        QueryIntent q10 = asQueryIntent(nlParser.parse(query10));
        log.info("解析结果: {}", q10);
        assertNotNull(q10.getPagination());
        assertEquals(20, q10.getPagination().getOffset());
        assertEquals(10, q10.getPagination().getLimit());
        log.info("✓ 场景10通过：offset + limit 分页解析正确");

        // 场景11: 页码分页 - page + size（自动计算offset）
        log.info("\n【场景11】页码分页 - page + size");
        String query11 = "年龄大于18，第3页，每页10条";
        log.info("查询: {}", query11);
        QueryIntent q11 = asQueryIntent(nlParser.parse(query11));
        log.info("解析结果: {}", q11);
        assertNotNull(q11.getPagination());
        assertEquals(3, q11.getPagination().getPage());
        assertEquals(10, q11.getPagination().getSize());
        assertEquals(20, q11.getPagination().getOffset()); // 第3页 = 跳过前20条
        assertEquals(10, q11.getPagination().getLimit());
        log.info("✓ 场景11通过：page + size 分页解析正确，自动计算offset");

        // 场景12: 范围分页 - "返回第21到30条"
        log.info("\n【场景12】范围分页");
        String query12 = "年龄大于18，返回第21到30条";
        log.info("查询: {}", query12);
        QueryIntent q12 = asQueryIntent(nlParser.parse(query12));
        log.info("解析结果: {}", q12);
        assertNotNull(q12.getPagination());
        assertEquals(20, q12.getPagination().getOffset()); // 第21条 = 跳过前20条
        assertEquals(10, q12.getPagination().getLimit()); // 30-21+1 = 10条
        log.info("✓ 场景12通过：范围分页解析正确，自动计算offset和limit");

        // 场景13: ES search_after 续查
        log.info("\n【场景13】ES search_after 续查");
        String query13 = "年龄大于18，继续查询，返回10条";
        log.info("查询: {}", query13);
        QueryIntent q13 = asQueryIntent(nlParser.parse(query13));
        log.info("解析结果: {}", q13);
        assertNotNull(q13.getPagination());
        assertTrue(q13.getPagination().getContinueSearch());
        assertEquals(10, q13.getPagination().getLimit());
        assertNull(q13.getPagination().getOffset()); // search_after不使用offset
        log.info("✓ 场景13通过：search_after 续查解析正确");

        // 场景14: 终极复杂场景 - 索引 + 多条件 + IN + OR + BETWEEN + 排序 + 分页
        log.info("\n【场景14】终极复杂场景 - 全功能综合测试");
        String query14 = "帮我查一下user_profile这个索引，年龄大于等于18并且年龄小于等于60，" +
                "城市在北京、上海、深圳、广州，" +
                "名字包含张或李或王，" +
                "积分介于100,1000，" +
                "按创建时间降序，按年龄升序，" +
                "跳过50条，返回20条";
        log.info("查询: {}", query14);
        QueryIntent q14 = asQueryIntent(nlParser.parse(query14));
        log.info("解析结果: {}", q14);

        // 验证索引
        assertEquals("user_profile", q14.getIndexHint());

        // 验证条件
        assertTrue(q14.hasCondition());
        ConditionIntent c14 = q14.getCondition();
        assertEquals(LogicType.AND, c14.getLogic());
        assertTrue(findOperatorInCondition(c14, OperatorType.GTE));
        assertTrue(findOperatorInCondition(c14, OperatorType.LTE));
        assertTrue(findOperatorInCondition(c14, OperatorType.IN));
        assertTrue(findOperatorInCondition(c14, OperatorType.LIKE));
        assertTrue(findOperatorInCondition(c14, OperatorType.BETWEEN));

        // 验证排序（多字段排序）
        assertTrue(q14.hasSort());
        assertEquals(2, q14.getSorts().size());
        assertEquals("创建时间", q14.getSorts().get(0).getFieldHint());
        assertEquals(SortOrder.DESC, q14.getSorts().get(0).getOrder());
        assertEquals("年龄", q14.getSorts().get(1).getFieldHint());
        assertEquals(SortOrder.ASC, q14.getSorts().get(1).getOrder());

        // 验证分页
        assertTrue(q14.hasPagination());
        assertEquals(50, q14.getPagination().getOffset());
        assertEquals(20, q14.getPagination().getLimit());

        log.info("✓ 场景14通过：终极复杂场景全功能解析正确");

        log.info("\n========== ✓ 超级端到端测试全部通过 (14个场景) ==========");
    }

    // ==================== 错误处理测试 ====================

    @Test
    @Order(10)
    @DisplayName("错误处理 - 空查询")
    void testEmptyQuery() {
        log.info("========== 测试：空查询 ==========");
        assertThrows(io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException.class, () -> {
            nlParser.parse("");
        });
        log.info("✓ 空查询异常处理正确");
    }

    @Test
    @Order(11)
    @DisplayName("错误处理 - null查询")
    void testNullQuery() {
        log.info("========== 测试：null查询 ==========");
        assertThrows(io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException.class, () -> {
            nlParser.parse(null);
        });
        log.info("✓ null查询异常处理正确");
    }

    @Test
    @Order(12)
    @DisplayName("错误处理 - 只有停用词")
    void testOnlyStopWords() {
        log.info("========== 测试：只有停用词 ==========");
        assertThrows(io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException.class, () -> {
            nlParser.parse("查一下");
        });
        log.info("✓ 只有停用词异常处理正确");
    }

    // ==================== 辅助方法 ====================

    /**
     * 递归查找条件树中是否包含指定操作符
     */
    private boolean findOperatorInCondition(ConditionIntent condition, OperatorType operator) {
        if (condition == null) {
            return false;
        }
        if (condition.getOperator() == operator) {
            return true;
        }
        if (condition.getChildren() != null) {
            for (ConditionIntent child : condition.getChildren()) {
                if (findOperatorInCondition(child, operator)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 递归查找条件树中是否包含指定值
     */
    private boolean findValueInCondition(ConditionIntent condition, Object value) {
        if (condition == null) {
            return false;
        }
        if (value.equals(condition.getValue())) {
            return true;
        }
        if (condition.getValues() != null && condition.getValues().contains(value)) {
            return true;
        }
        if (condition.getChildren() != null) {
            for (ConditionIntent child : condition.getChildren()) {
                if (findValueInCondition(child, value)) {
                    return true;
                }
            }
        }
        return false;
    }
}
