package io.github.surezzzzzz.sdk.naturallanguage.parser.test.cases;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.LogicType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.OperatorType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.SearchAfterMode;
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

    @Autowired
    private io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.NLTokenizer tokenizer;

    @Autowired
    private io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry keywordRegistry;

    private QueryIntent asQueryIntent(Intent intent) {
        log.info("解析得到的Intent类型: {}", intent != null ? intent.getClass().getSimpleName() : "null");
        if (intent instanceof AnalyticsIntent) {
            AnalyticsIntent ai = (AnalyticsIntent) intent;
            log.info("AnalyticsIntent内容: aggregations={}", ai.getAggregations());
        }
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
        assertEquals(10, q1.getPagination().getSize());
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

        // 场景8: 自然语言查询（口语化）
        log.info("\n【场景8】自然语言查询（口语化）");
        String query8 = "帮我查一下年龄大于等于18小于60，城市在北京、上海、深圳并且名字包含张或李，按创建时间降序，返回前10条数据就行";
        log.info("查询: {}", query8);
        QueryIntent q8 = asQueryIntent(nlParser.parse(query8));
        log.info("解析结果: {}", q8);
        assertTrue(q8.hasCondition() && q8.hasSort() && q8.hasPagination());
        assertTrue(findValueInCondition(q8.getCondition(), 18L) && findValueInCondition(q8.getCondition(), 60L));
        assertTrue(findOperatorInCondition(q8.getCondition(), OperatorType.IN));
        assertEquals(10, q8.getPagination().getSize());
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
        assertEquals(20L, q10.getPagination().getOffset());
        assertEquals(10, q10.getPagination().getSize());
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
        assertEquals(20L, q11.getPagination().getOffset());
        log.info("✓ 场景11通过：page + size 分页解析正确，自动计算offset");

        // 场景12: 范围分页 - "返回第21到30条"
        log.info("\n【场景12】范围分页");
        String query12 = "年龄大于18，返回第21到30条";
        log.info("查询: {}", query12);
        QueryIntent q12 = asQueryIntent(nlParser.parse(query12));
        log.info("解析结果: {}", q12);
        assertNotNull(q12.getPagination());
        assertEquals(20L, q12.getPagination().getOffset());
        assertEquals(10, q12.getPagination().getSize());
        log.info("✓ 场景12通过：范围分页解析正确，自动计算offset和size");

        // 场景13: search_after 续查
        log.info("\n【场景13】search_after 续查");
        String query13 = "年龄大于18，继续查询，返回10条";
        log.info("查询: {}", query13);
        QueryIntent q13 = asQueryIntent(nlParser.parse(query13));
        log.info("解析结果: {}", q13);
        assertNotNull(q13.getPagination());
        assertEquals(SearchAfterMode.TIEBREAKER, q13.getPagination().getSearchAfterMode());
        assertEquals(10, q13.getPagination().getSize());
        assertNull(q13.getPagination().getOffset());
        log.info("✓ 场景13通过：search_after 续查解析正确");

        // 场景14: 终极复杂场景
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

        assertEquals("user_profile", q14.getIndexHint());
        assertTrue(q14.hasCondition());
        ConditionIntent c14 = q14.getCondition();
        assertEquals(LogicType.AND, c14.getLogic());
        assertTrue(findOperatorInCondition(c14, OperatorType.GTE));
        assertTrue(findOperatorInCondition(c14, OperatorType.LTE));
        assertTrue(findOperatorInCondition(c14, OperatorType.IN));
        assertTrue(findOperatorInCondition(c14, OperatorType.LIKE));
        assertTrue(findOperatorInCondition(c14, OperatorType.BETWEEN));

        assertTrue(q14.hasSort());
        assertEquals(2, q14.getSorts().size());
        assertEquals("创建时间", q14.getSorts().get(0).getFieldHint());
        assertEquals(SortOrder.DESC, q14.getSorts().get(0).getOrder());
        assertEquals("年龄", q14.getSorts().get(1).getFieldHint());
        assertEquals(SortOrder.ASC, q14.getSorts().get(1).getOrder());

        assertTrue(q14.hasPagination());
        assertEquals(50L, q14.getPagination().getOffset());
        assertEquals(20, q14.getPagination().getSize());

        log.info("✓ 场景14通过：终极复杂场景全功能解析正确");
        log.info("\n========== ✓ 超级端到端测试全部通过 (14个场景) ==========");
    }

    // ==================== 字段折叠(Collapse)测试 ====================

    @Test
    @Order(2)
    @DisplayName("字段折叠(去重)测试")
    void testCollapseFeature() {
        log.info("========== 字段折叠(去重)测试 ==========");

        String query1 = "按源IP去重";
        QueryIntent q1 = asQueryIntent(nlParser.parse(query1));
        assertTrue(q1.hasCollapse());
        assertEquals("源IP", q1.getCollapse().getFieldHint());
        log.info("✓ 场景1通过：按字段去重解析正确");

        String query2 = "源IP去重";
        QueryIntent q2 = asQueryIntent(nlParser.parse(query2));
        assertTrue(q2.hasCollapse());
        assertEquals("源IP", q2.getCollapse().getFieldHint());
        log.info("✓ 场景2通过：字段在前解析正确");

        String query3 = "去重源IP";
        QueryIntent q3 = asQueryIntent(nlParser.parse(query3));
        assertTrue(q3.hasCollapse());
        assertEquals("源IP", q3.getCollapse().getFieldHint());
        log.info("✓ 场景3通过：去重在前解析正确");

        String query4 = "用户名唯一";
        QueryIntent q4 = asQueryIntent(nlParser.parse(query4));
        assertTrue(q4.hasCollapse());
        assertEquals("用户名", q4.getCollapse().getFieldHint());
        log.info("✓ 场景4通过：唯一关键词解析正确");

        String query5 = "年龄大于18按用户名去重按创建时间降序返回100条";
        QueryIntent q5 = asQueryIntent(nlParser.parse(query5));
        assertTrue(q5.hasCondition());
        assertTrue(q5.hasCollapse());
        assertTrue(q5.hasSort());
        assertTrue(q5.hasPagination());
        assertEquals("用户名", q5.getCollapse().getFieldHint());
        assertEquals("创建时间", q5.getSorts().get(0).getFieldHint());
        assertEquals(SortOrder.DESC, q5.getSorts().get(0).getOrder());
        assertEquals(100, q5.getPagination().getSize());
        log.info("✓ 场景5通过：组合查询解析正确");

        log.info("\n========== ✓ 字段折叠(去重)测试全部通过 (5个场景) ==========");
    }

    // ==================== Bug修复测试 ====================

    /**
     * Bug1: OR条件字段错乱
     * 输入："年龄小于25或城市等于深圳"
     * 期望：age < 25 OR city = "深圳"（两个condition，logic=or）
     */
    @Test
    @Order(5)
    @DisplayName("Bug1修复：跨字段OR（数字+或+中文）")
    void testCrossFieldOrWithDigit() {
        log.info("========== Bug1修复测试 ==========");

        String query = "年龄小于25或城市等于深圳";
        log.info("查询: {}", query);
        ConditionIntent cond = asQueryIntent(nlParser.parse(query)).getCondition();
        log.info("解析结果: {}", cond);

        assertEquals(LogicType.OR, cond.getLogic(), "逻辑应为OR");
        assertFalse(cond.getChildren().isEmpty(), "应有子条件");
        assertEquals(2, cond.getChildren().size(), "应有2个子条件");

        // 第一个子条件: age < 25
        ConditionIntent c1 = cond.getChildren().get(0);
        assertEquals("年龄", c1.getFieldHint());
        assertEquals(OperatorType.LT, c1.getOperator());
        assertEquals(25L, c1.getValue());

        // 第二个子条件: city = "深圳"
        ConditionIntent c2 = cond.getChildren().get(1);
        assertEquals("城市", c2.getFieldHint());
        assertEquals(OperatorType.EQ, c2.getOperator());
        assertEquals("深圳", c2.getValue());

        log.info("✓ Bug1修复验证通过");
    }

    /**
     * Bug1变体：积分+或+城市
     */
    @Test
    @Order(6)
    @DisplayName("Bug1修复：积分大于100或城市等于北京")
    void testCrossFieldOrWithDigitVariant() {
        String query = "积分大于100或城市等于北京";
        log.info("查询: {}", query);
        ConditionIntent cond = asQueryIntent(nlParser.parse(query)).getCondition();

        assertEquals(LogicType.OR, cond.getLogic());
        assertEquals(2, cond.getChildren().size());
        assertEquals("积分", cond.getChildren().get(0).getFieldHint());
        assertEquals(100L, cond.getChildren().get(0).getValue());
        assertEquals("城市", cond.getChildren().get(1).getFieldHint());
        assertEquals("北京", cond.getChildren().get(1).getValue());
        log.info("✓ Bug1变体验证通过");
    }

    /**
     * Bug2: IN列表多含"中"
     * 输入："城市在北京、上海、深圳中"
     * 期望：city IN ["北京", "上海", "深圳"]
     */
    @Test
    @Order(7)
    @DisplayName("Bug2修复：IN列表末尾的「中」应被忽略")
    void testInWithTrailingZhong() {
        log.info("========== Bug2修复测试 ==========");

        String query = "城市在北京、上海、深圳中";
        log.info("查询: {}", query);
        ConditionIntent cond = asQueryIntent(nlParser.parse(query)).getCondition();
        log.info("解析结果: {}", cond);

        assertEquals(OperatorType.IN, cond.getOperator());
        assertEquals("城市", cond.getFieldHint());
        assertNotNull(cond.getValues());
        assertEquals(3, cond.getValues().size(), "IN列表应有3个值，不含末尾的'中'");
        assertEquals("北京", cond.getValues().get(0));
        assertEquals("上海", cond.getValues().get(1));
        assertEquals("深圳", cond.getValues().get(2));

        log.info("✓ Bug2修复验证通过");
    }

    // ==================== 错误处理测试 ====================

    @Test
    @Order(10)
    @DisplayName("错误处理 - 空查询")
    void testEmptyQuery() {
        assertThrows(io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException.class, () -> {
            nlParser.parse("");
        });
    }

    @Test
    @Order(11)
    @DisplayName("错误处理 - null查询")
    void testNullQuery() {
        assertThrows(io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException.class, () -> {
            nlParser.parse(null);
        });
    }

    @Test
    @Order(12)
    @DisplayName("错误处理 - 只有停用词")
    void testOnlyStopWords() {
        assertThrows(io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException.class, () -> {
            nlParser.parse("的呢吧");
        });
    }

    // ==================== Search 集成测试覆盖 ====================

    /**
     * 覆盖 search-starter NLIntegrationTest 的全部场景
     * 确保解析结果能被 IntentTranslator 正确翻译
     */
    @Test
    @Order(8)
    @DisplayName("Search 集成场景覆盖")
    void testSearchIntegrationScenarios() {
        log.info("========== Search 集成场景覆盖 ==========");

        // 2.1 多条件AND
        ConditionIntent c1 = asQueryIntent(nlParser.parse("状态等于active并且年龄大于等于25")).getCondition();
        log.info("2.1: {}", c1);
        assertNotNull(c1);
        assertEquals(LogicType.AND, c1.getLogic());

        // 2.2 三条件AND
        ConditionIntent c2 = asQueryIntent(nlParser.parse("城市等于北京并且状态等于active并且积分大于100")).getCondition();
        log.info("2.2: {}", c2);
        assertNotNull(c2);

        // 3.1 IN + trailing "中"
        ConditionIntent c3 = asQueryIntent(nlParser.parse("城市在北京、上海、深圳中")).getCondition();
        log.info("3.1: {}", c3);
        assertNotNull(c3);
        assertEquals(OperatorType.IN, c3.getOperator());
        assertEquals(3, c3.getValues().size());

        // 3.2 IN + AND
        ConditionIntent c4 = asQueryIntent(nlParser.parse("城市在北京、上海、深圳中并且年龄大于25")).getCondition();
        log.info("3.2: {}", c4);
        assertNotNull(c4);
        assertTrue(findOperatorInCondition(c4, OperatorType.IN));

        // 4.1 跨字段 OR
        ConditionIntent c5 = asQueryIntent(nlParser.parse("年龄小于25或城市等于深圳")).getCondition();
        log.info("4.1: {}", c5);
        assertNotNull(c5);
        assertEquals(LogicType.OR, c5.getLogic());

        // 4.2 复杂嵌套 OR
        ConditionIntent c6 = asQueryIntent(nlParser.parse("状态等于active并且年龄大于30或者状态等于active并且城市等于北京")).getCondition();
        log.info("4.2: {}", c6);
        assertNotNull(c6);

        // 10.1 BETWEEN "在...到...之间"
        ConditionIntent c7 = asQueryIntent(nlParser.parse("年龄在18到30之间")).getCondition();
        log.info("10.1: {}", c7);
        assertNotNull(c7, "BETWEEN '在...到...之间' 应产生条件");
        assertTrue(findOperatorInCondition(c7, OperatorType.BETWEEN),
                "应包含 BETWEEN 操作符，实际: " + (c7.getOperator() != null ? c7.getOperator() : "null"));

        // 9.1 字段绑定
        ConditionIntent c8 = asQueryIntent(nlParser.parse("年龄大于25")).getCondition();
        log.info("9.1: {}", c8);
        assertNotNull(c8);
        assertEquals(OperatorType.GT, c8.getOperator());

        log.info("========== Search 集成场景覆盖全部通过 ==========");
    }

    // ==================== 索引提取测试 ====================

    @Test
    @Order(9)
    @DisplayName("索引提取 - app_access_log-*索引")
    void testIndexExtraction() {
        log.info("========== 索引提取测试 ==========");

        // 简单索引名
        String query1 = "查询user_profile这个索引，年龄大于18";
        QueryIntent q1 = asQueryIntent(nlParser.parse(query1));
        log.info("查询: {}, indexHint: {}", query1, q1.getIndexHint());
        assertEquals("user_profile", q1.getIndexHint());

        // 带通配符的索引名
        String query2 = "查询app_access_log-*索引，clientIP等于192.168.1.1";
        // 先看 token 流
        java.util.List<io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token> tokens2 =
                tokenizer.tokenize(query2, keywordRegistry);
        for (int i = 0; i < tokens2.size(); i++) {
            io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token t = tokens2.get(i);
            log.info("  token[{}]: type={}, text='{}', value={}", i, t.getType(), t.getText(), t.getValue());
        }
        QueryIntent q2 = asQueryIntent(nlParser.parse(query2));
        log.info("查询: {}, indexHint: {}", query2, q2.getIndexHint());
        assertNotNull(q2.getIndexHint(), "应提取出索引名，实际: null");
        assertTrue(q2.getIndexHint().startsWith("app_access_log"), "索引名应以 app_access_log 开头");

        log.info("✓ 索引提取测试通过");
    }

    // ==================== v1.1.4 新增测试：不匹配操作符 ====================

    @Test
    @Order(20)
    @DisplayName("NOT_PREFIX - 前缀不匹配")
    void testNotPrefixOperator() {
        log.info("========== 测试：NOT_PREFIX 前缀不匹配 ==========");

        // "开头不是" 触发 NOT_PREFIX
        ConditionIntent c1 = asQueryIntent(nlParser.parse("名字开头不是张")).getCondition();
        log.info("查询: 名字开头不是张, 结果: {}", c1);
        assertEquals(OperatorType.NOT_PREFIX, c1.getOperator());
        assertEquals("名字", c1.getFieldHint());
        assertEquals("张", c1.getValue());

        log.info("✓ NOT_PREFIX 测试通过");
    }

    @Test
    @Order(21)
    @DisplayName("NOT_SUFFIX - 后缀不匹配")
    void testNotSuffixOperator() {
        log.info("========== 测试：NOT_SUFFIX 后缀不匹配 ==========");

        // "结尾不是" 触发 NOT_SUFFIX
        ConditionIntent c1 = asQueryIntent(nlParser.parse("邮箱结尾不是abc")).getCondition();
        log.info("查询: 邮箱结尾不是abc, 结果: {}", c1);
        assertEquals(OperatorType.NOT_SUFFIX, c1.getOperator());
        assertEquals("邮箱", c1.getFieldHint());
        assertEquals("abc", c1.getValue());

        log.info("✓ NOT_SUFFIX 测试通过");
    }

    @Test
    @Order(22)
    @DisplayName("NOT_PREFIX/SUFFIX 与正向操作符不冲突")
    void testNotOperatorsNoConflict() {
        log.info("========== 测试：不匹配操作符与正向操作符不冲突 ==========");

        // 正向 PREFIX 不被 NOT_PREFIX 吞掉
        ConditionIntent c1 = asQueryIntent(nlParser.parse("名字开头是张")).getCondition();
        assertEquals(OperatorType.PREFIX, c1.getOperator());
        assertEquals("张", c1.getValue());

        // 正向 SUFFIX 不被 NOT_SUFFIX 吞掉
        ConditionIntent c2 = asQueryIntent(nlParser.parse("邮箱结尾是abc")).getCondition();
        assertEquals(OperatorType.SUFFIX, c2.getOperator());
        assertEquals("abc", c2.getValue());

        log.info("✓ 不冲突测试通过");
    }

    @Test
    @Order(23)
    @DisplayName("NOT_PREFIX/SUFFIX 与比较运算符组合")
    void testNotOperatorsWithComparison() {
        log.info("========== 测试：NOT_PREFIX + 比较 ==========");

        // NOT_PREFIX + 比较 + OR
        ConditionIntent c1 = asQueryIntent(
                nlParser.parse("名字开头不是张或者年龄大于18")).getCondition();
        log.info("查询: 名字开头不是张或者年龄大于18, 结果: {}", c1);
        assertEquals(LogicType.OR, c1.getLogic());
        assertTrue(findOperatorInCondition(c1, OperatorType.NOT_PREFIX));
        assertTrue(findOperatorInCondition(c1, OperatorType.GT));

        log.info("✓ 组合测试通过");
    }

    // ==================== 辅助方法 ====================

    private boolean findOperatorInCondition(ConditionIntent condition, OperatorType operator) {
        if (condition == null) return false;
        if (condition.getOperator() == operator) return true;
        if (condition.getChildren() != null) {
            for (ConditionIntent child : condition.getChildren()) {
                if (findOperatorInCondition(child, operator)) return true;
            }
        }
        return false;
    }

    private boolean findValueInCondition(ConditionIntent condition, Object value) {
        if (condition == null) return false;
        if (value.equals(condition.getValue())) return true;
        if (condition.getValues() != null && condition.getValues().contains(value)) return true;
        if (condition.getChildren() != null) {
            for (ConditionIntent child : condition.getChildren()) {
                if (findValueInCondition(child, value)) return true;
            }
        }
        return false;
    }
}
