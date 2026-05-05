package io.github.surezzzzzz.sdk.naturallanguage.parser.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.naturallanguage.parser.binder.FieldBinder;
import io.github.surezzzzzz.sdk.naturallanguage.parser.binder.TranslateContext;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.SearchAfterMode;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.NLParser;
import io.github.surezzzzzz.sdk.naturallanguage.parser.test.NaturalLanguageParserTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试：验证 nl-parser 解析出的 Intent 能被正确翻译为 search-core 的 QueryRequest / AggRequest
 * <p>
 * 使用 search-core 模型类（纯 POJO，不依赖 ES 客户端），同时作为 FieldBinder + IntentTranslator 接入的最佳实践示例。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = NaturalLanguageParserTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchIntentTranslatorTest {

    @Autowired
    private NLParser nlParser;

    private SearchIntentTranslator translator;
    private SimpleFieldBinder fieldBinder;

    @BeforeEach
    void init() {
        fieldBinder = new SimpleFieldBinder();
        translator = new SearchIntentTranslator(fieldBinder);
    }

    @BeforeAll
    static void setup() {
        log.info("========== SearchIntentTranslatorTest 开始 ==========");
    }

    @AfterAll
    static void teardown() {
        log.info("========== SearchIntentTranslatorTest 完成 ==========");
    }

    // ==================== IntentTranslator 实现（最佳实践示例）====================

    /**
     * Intent → search-core QueryRequest / AggRequest 翻译器
     * <p>
     * 展示如何将 nl-parser 的 Intent 转换为 search-core 的标准请求模型
     */
    static class SearchIntentTranslator {

        private final FieldBinder fieldBinder;

        SearchIntentTranslator(FieldBinder fieldBinder) {
            this.fieldBinder = fieldBinder;
        }

        Object translate(Intent intent, TranslateContext context) {
            if (intent instanceof QueryIntent) {
                return translateQuery((QueryIntent) intent, context);
            }
            if (intent instanceof AnalyticsIntent) {
                return translateAnalytics((AnalyticsIntent) intent, context);
            }
            throw NLParseException.unsupportedIntent(intent.getClass().getSimpleName());
        }

        private QueryRequest translateQuery(QueryIntent intent, TranslateContext context) {
            String index = context.getDataSource() != null ? context.getDataSource() : intent.getIndexHint();
            QueryRequest.QueryRequestBuilder builder = QueryRequest.builder().index(index);

            if (intent.hasCondition()) {
                builder.query(translateCondition(intent.getCondition(), context));
            }
            if (intent.hasDateRange()) {
                DateRangeIntent dr = intent.getDateRange();
                builder.dateRange(QueryRequest.DateRange.builder()
                        .from(dr.getFrom()).to(dr.getTo()).build());
            }
            if (intent.hasCollapse()) {
                String field = fieldBinder.bind(intent.getCollapse().getFieldHint(), index);
                builder.collapse(QueryRequest.CollapseField.builder().field(field).build());
            }
            builder.pagination(translatePagination(intent.getPagination(), intent.getSorts(), index));
            return builder.build();
        }

        private AggRequest translateAnalytics(AnalyticsIntent intent, TranslateContext context) {
            String index = context.getDataSource() != null ? context.getDataSource() : intent.getIndexHint();
            AggRequest.AggRequestBuilder builder = AggRequest.builder().index(index);

            if (intent.hasCondition()) {
                builder.query(translateCondition(intent.getCondition(), context));
            }
            if (intent.hasAggregation()) {
                List<AggDefinition> defs = new ArrayList<>();
                for (AggregationIntent agg : intent.getAggregations()) {
                    defs.add(translateAgg(agg, index));
                }
                builder.aggs(defs);
            }
            return builder.build();
        }

        private QueryCondition translateCondition(ConditionIntent condition, TranslateContext context) {
            if (condition == null) return null;
            String index = context.getDataSource();

            if (condition.isLogicCondition()) {
                List<QueryCondition> children = new ArrayList<>();
                if (condition.getOperator() != null) {
                    children.add(QueryCondition.builder()
                            .field(fieldBinder.bind(condition.getFieldHint(), index))
                            .op(condition.getOperator().getCode())
                            .value(condition.getValue())
                            .values(condition.getValues())
                            .build());
                }
                for (ConditionIntent child : condition.getChildren()) {
                    children.add(translateCondition(child, context));
                }
                return QueryCondition.builder()
                        .logic(condition.getLogic().getCode())
                        .conditions(children)
                        .build();
            }

            return QueryCondition.builder()
                    .field(fieldBinder.bind(condition.getFieldHint(), index))
                    .op(condition.getOperator().getCode())
                    .value(condition.getValue())
                    .values(condition.getValues())
                    .build();
        }

        private AggDefinition translateAgg(AggregationIntent agg, String index) {
            String field = agg.getGroupByFieldHint() != null
                    ? fieldBinder.bind(agg.getGroupByFieldHint(), index)
                    : fieldBinder.bind(agg.getFieldHint(), index);

            AggDefinition.AggDefinitionBuilder builder = AggDefinition.builder()
                    .name(agg.getNameHint() != null ? agg.getNameHint()
                            : agg.getType().getCode() + "_" + field)
                    .type(agg.getType().getCode())
                    .field(field);

            if (agg.getSize() != null) builder.size(agg.getSize());
            if (agg.getInterval() != null) builder.interval(agg.getInterval());

            if (!agg.getSubAggs().isEmpty()) {
                List<AggDefinition> subDefs = new ArrayList<>();
                for (AggregationIntent sub : agg.getSubAggs()) {
                    subDefs.add(translateAgg(sub, index));
                }
                builder.aggs(subDefs);
            }
            return builder.build();
        }

        private PaginationInfo translatePagination(PaginationIntent pagination,
                                                   List<SortIntent> sorts, String index) {
            PaginationInfo.PaginationInfoBuilder builder = PaginationInfo.builder();
            final int defaultSize = 20;

            if (pagination != null && (
                    pagination.getSearchAfter() != null ||
                    (pagination.getSearchAfterMode() != null
                            && pagination.getSearchAfterMode() != SearchAfterMode.NONE))) {
                builder.type("search_after")
                        .searchAfter(pagination.getSearchAfter())
                        .size(pagination.getSize() != null ? pagination.getSize() : defaultSize);
                if (pagination.getSearchAfterMode() != null) {
                    builder.searchAfterMode(pagination.getSearchAfterMode().getCode());
                }
            } else {
                builder.type("offset");
                if (pagination != null && pagination.getPage() != null) {
                    builder.page(pagination.getPage())
                            .size(pagination.getSize() != null ? pagination.getSize() : defaultSize);
                } else if (pagination != null && pagination.getOffset() != null && pagination.getSize() != null) {
                    int page = (int) (pagination.getOffset() / pagination.getSize()) + 1;
                    builder.page(page).size(pagination.getSize());
                } else if (pagination != null && pagination.getSize() != null) {
                    builder.page(1).size(pagination.getSize());
                } else {
                    builder.page(1).size(defaultSize);
                }
            }

            if (sorts != null && !sorts.isEmpty()) {
                List<PaginationInfo.SortField> sortFields = new ArrayList<>();
                for (SortIntent sort : sorts) {
                    sortFields.add(PaginationInfo.SortField.builder()
                            .field(fieldBinder.bind(sort.getFieldHint(), index))
                            .order(sort.getOrder().getCode())
                            .build());
                }
                builder.sort(sortFields);
            }
            return builder.build();
        }
    }

    // ==================== 测试辅助 ====================

    private TranslateContext ctx(String index) {
        return TranslateContext.builder().dataSource(index).fieldBinder(fieldBinder).build();
    }

    // ==================== 1. 查询意图 → QueryRequest ====================

    @Test
    @Order(1)
    @DisplayName("查询意图 → QueryRequest：多条件 + 排序 + 分页")
    void testQueryIntentToQueryRequest() {
        String nl = "年龄大于等于18并且城市在北京、上海、深圳，按创建时间降序，返回前50条";
        log.info("NL: {}", nl);

        Intent intent = nlParser.parse(nl);
        assertTrue(intent instanceof QueryIntent);
        QueryIntent qi = (QueryIntent) intent;

        QueryRequest req = (QueryRequest) translator.translate(qi, ctx("users"));
        log.info("QueryRequest: {}", req);

        assertEquals("users", req.getIndex());
        assertNotNull(req.getQuery());
        assertEquals("and", req.getQuery().getLogic());
        assertFalse(req.getQuery().getConditions().isEmpty());
        assertNotNull(req.getPagination());
        assertEquals(50, req.getPagination().getSize());
        assertNotNull(req.getPagination().getSort());
        assertEquals(1, req.getPagination().getSort().size());
        assertEquals("createTime", req.getPagination().getSort().get(0).getField());
        assertEquals("desc", req.getPagination().getSort().get(0).getOrder());

        log.info("✓ 查询意图 → QueryRequest 验证通过");
    }

    @Test
    @Order(2)
    @DisplayName("查询意图 → QueryRequest：字段折叠 + 去重")
    void testQueryWithCollapse() {
        String nl = "状态等于active，按源IP去重，按创建时间降序，返回前50条";
        log.info("NL: {}", nl);

        Intent intent = nlParser.parse(nl);
        assertTrue(intent instanceof QueryIntent);

        QueryRequest req = (QueryRequest) translator.translate(intent, ctx("logs"));
        log.info("QueryRequest: {}", req);

        assertEquals("logs", req.getIndex());
        assertNotNull(req.getQuery());
        assertEquals("eq", req.getQuery().getOp());
        assertEquals("status", req.getQuery().getField());
        assertEquals("active", req.getQuery().getValue());
        assertNotNull(req.getCollapse());
        assertEquals("sourceIp", req.getCollapse().getField());
        assertEquals(50, req.getPagination().getSize());
        assertEquals("createTime", req.getPagination().getSort().get(0).getField());

        log.info("✓ 字段折叠 → QueryRequest 验证通过");
    }

    @Test
    @Order(3)
    @DisplayName("聚合意图 → AggRequest：嵌套聚合 + 时间聚合")
    void testAnalyticsIntentToAggRequest() {
        String nl = "查询users索引，status等于active，" +
                "按城市分组前10名计算年龄平均值，按创建时间每天统计";
        log.info("NL: {}", nl);

        Intent intent = nlParser.parse(nl);
        assertTrue(intent instanceof AnalyticsIntent);
        AnalyticsIntent ai = (AnalyticsIntent) intent;

        AggRequest req = (AggRequest) translator.translate(ai, ctx(null));
        log.info("AggRequest: {}", req);

        assertEquals("users", req.getIndex());
        assertNotNull(req.getQuery());
        assertEquals("status", req.getQuery().getField());
        assertEquals("eq", req.getQuery().getOp());
        assertEquals("active", req.getQuery().getValue());
        assertNotNull(req.getAggs());
        assertEquals(2, req.getAggs().size());

        AggDefinition cityAgg = req.getAggs().get(0);
        assertEquals("terms", cityAgg.getType());
        assertEquals("city", cityAgg.getField());
        assertEquals(10, cityAgg.getSize());
        assertEquals(1, cityAgg.getAggs().size());
        AggDefinition avgAgg = cityAgg.getAggs().get(0);
        assertEquals("avg", avgAgg.getType());
        assertEquals("age", avgAgg.getField());

        AggDefinition dailyAgg = req.getAggs().get(1);
        assertEquals("date_histogram", dailyAgg.getType());
        assertEquals("createTime", dailyAgg.getField());
        assertEquals("1d", dailyAgg.getInterval());

        log.info("✓ 聚合意图 → AggRequest 验证通过");
    }

    @Test
    @Order(4)
    @DisplayName("indexHint 优先级：TranslateContext.dataSource > Intent.indexHint")
    void testIndexPriority() {
        String nl = "查询user_profile索引，年龄大于18";
        Intent intent = nlParser.parse(nl);

        QueryRequest req1 = (QueryRequest) translator.translate(intent, ctx("override_index"));
        assertEquals("override_index", req1.getIndex());

        QueryRequest req2 = (QueryRequest) translator.translate(intent, ctx(null));
        assertEquals("user_profile", req2.getIndex());

        log.info("✓ indexHint 优先级验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("FieldBinder：中文字段提示 → ES 字段名")
    void testFieldBinding() {
        String nl = "年龄大于18并且城市等于北京，按创建时间降序";
        Intent intent = nlParser.parse(nl);
        assertTrue(intent instanceof QueryIntent);

        QueryRequest req = (QueryRequest) translator.translate(intent, ctx("users"));
        log.info("QueryRequest: {}", req);

        QueryCondition cond = req.getQuery();
        assertEquals("and", cond.getLogic());
        boolean hasAge = false, hasCity = false;
        for (QueryCondition c : cond.getConditions()) {
            if ("age".equals(c.getField())) hasAge = true;
            if ("city".equals(c.getField())) hasCity = true;
        }
        assertTrue(hasAge, "年龄 应绑定为 age");
        assertTrue(hasCity, "城市 应绑定为 city");
        assertEquals("createTime", req.getPagination().getSort().get(0).getField());

        log.info("✓ FieldBinder 验证通过");
    }

    // ==================== 2. 操作符覆盖（18 种）====================

    @Test
    @Order(10)
    @DisplayName("操作符：ne 不等于")
    void testOperatorNe() {
        QueryRequest req = (QueryRequest) translator.translate(nlParser.parse("状态不等于active"), ctx("users"));
        assertEquals("ne", req.getQuery().getOp());
        assertEquals("status", req.getQuery().getField());
        assertEquals("active", req.getQuery().getValue());
        log.info("✓ ne 操作符验证通过");
    }

    @Test
    @Order(11)
    @DisplayName("操作符：lt / lte 小于 / 小于等于")
    void testOperatorLtLte() {
        QueryRequest lt = (QueryRequest) translator.translate(nlParser.parse("年龄小于60"), ctx("users"));
        assertEquals("lt", lt.getQuery().getOp());
        assertEquals("age", lt.getQuery().getField());
        assertEquals(60L, lt.getQuery().getValue());

        QueryRequest lte = (QueryRequest) translator.translate(nlParser.parse("年龄小于等于60"), ctx("users"));
        assertEquals("lte", lte.getQuery().getOp());
        assertEquals(60L, lte.getQuery().getValue());
        log.info("✓ lt/lte 操作符验证通过");
    }

    @Test
    @Order(12)
    @DisplayName("操作符：between 范围查询 → values:[from, to]")
    void testOperatorBetween() {
        QueryRequest req = (QueryRequest) translator.translate(nlParser.parse("年龄介于18,60"), ctx("users"));
        assertEquals("between", req.getQuery().getOp());
        assertEquals("age", req.getQuery().getField());
        assertNotNull(req.getQuery().getValues());
        assertEquals(2, req.getQuery().getValues().size());
        assertEquals(18L, req.getQuery().getValues().get(0));
        assertEquals(60L, req.getQuery().getValues().get(1));
        log.info("✓ between 操作符验证通过");
    }

    @Test
    @Order(13)
    @DisplayName("操作符：in 在列表中 → values 列表")
    void testOperatorIn() {
        QueryRequest req = (QueryRequest) translator.translate(
                nlParser.parse("城市在北京、上海、深圳"), ctx("users"));
        assertEquals("in", req.getQuery().getOp());
        assertEquals("city", req.getQuery().getField());
        assertNotNull(req.getQuery().getValues());
        assertEquals(3, req.getQuery().getValues().size());
        log.info("✓ in 操作符验证通过");
    }

    @Test
    @Order(14)
    @DisplayName("操作符：like / not_like 模糊匹配")
    void testOperatorLikeNotLike() {
        QueryRequest like = (QueryRequest) translator.translate(nlParser.parse("名字包含张"), ctx("users"));
        assertEquals("like", like.getQuery().getOp());
        assertEquals("name", like.getQuery().getField());

        QueryRequest notLike = (QueryRequest) translator.translate(nlParser.parse("名字不包含张"), ctx("users"));
        assertEquals("not_like", notLike.getQuery().getOp());
        assertEquals("name", notLike.getQuery().getField());
        log.info("✓ like/not_like 操作符验证通过");
    }

    @Test
    @Order(15)
    @DisplayName("操作符：prefix / suffix 前缀 / 后缀匹配")
    void testOperatorPrefixSuffix() {
        QueryRequest prefix = (QueryRequest) translator.translate(nlParser.parse("名字开头是张"), ctx("users"));
        assertEquals("prefix", prefix.getQuery().getOp());
        assertEquals("name", prefix.getQuery().getField());

        QueryRequest suffix = (QueryRequest) translator.translate(nlParser.parse("名字结尾是三"), ctx("users"));
        assertEquals("suffix", suffix.getQuery().getOp());
        assertEquals("name", suffix.getQuery().getField());
        log.info("✓ prefix/suffix 操作符验证通过");
    }

    @Test
    @Order(16)
    @DisplayName("操作符：exists / not_exists 字段存在")
    void testOperatorExistsNotExists() {
        QueryRequest exists = (QueryRequest) translator.translate(nlParser.parse("邮箱存在"), ctx("users"));
        assertEquals("exists", exists.getQuery().getOp());
        assertEquals("email", exists.getQuery().getField());
        assertNull(exists.getQuery().getValue());

        QueryRequest notExists = (QueryRequest) translator.translate(nlParser.parse("邮箱不存在"), ctx("users"));
        assertEquals("not_exists", notExists.getQuery().getOp());
        assertEquals("email", notExists.getQuery().getField());
        log.info("✓ exists/not_exists 操作符验证通过");
    }

    @Test
    @Order(17)
    @DisplayName("操作符：is_null / is_not_null 空值判断")
    void testOperatorIsNullIsNotNull() {
        QueryRequest isNull = (QueryRequest) translator.translate(nlParser.parse("邮箱为空"), ctx("users"));
        assertEquals("is_null", isNull.getQuery().getOp());
        assertEquals("email", isNull.getQuery().getField());

        QueryRequest isNotNull = (QueryRequest) translator.translate(nlParser.parse("邮箱不为空"), ctx("users"));
        assertEquals("is_not_null", isNotNull.getQuery().getOp());
        assertEquals("email", isNotNull.getQuery().getField());
        log.info("✓ is_null/is_not_null 操作符验证通过");
    }

    // ==================== 3. 分页策略 ====================

    @Test
    @Order(20)
    @DisplayName("分页：offset page+size")
    void testPaginationPage() {
        QueryRequest req = (QueryRequest) translator.translate(
                nlParser.parse("年龄大于18，第3页，每页10条"), ctx("users"));
        PaginationInfo p = req.getPagination();
        assertEquals("offset", p.getType());
        assertEquals(3, p.getPage());
        assertEquals(10, p.getSize());
        log.info("✓ offset page+size 分页验证通过");
    }

    @Test
    @Order(21)
    @DisplayName("分页：offset offset+size 自动转 page")
    void testPaginationOffset() {
        QueryRequest req = (QueryRequest) translator.translate(
                nlParser.parse("年龄大于18，跳过20条，返回10条"), ctx("users"));
        PaginationInfo p = req.getPagination();
        assertEquals("offset", p.getType());
        assertEquals(10, p.getSize());
        assertEquals(3, p.getPage());
        log.info("✓ offset+size → page 分页验证通过");
    }

    @Test
    @Order(22)
    @DisplayName("分页：search_after 深分页")
    void testPaginationSearchAfter() {
        QueryRequest req = (QueryRequest) translator.translate(
                nlParser.parse("年龄大于18，继续查询，返回10条"), ctx("users"));
        PaginationInfo p = req.getPagination();
        assertEquals("search_after", p.getType());
        assertEquals(10, p.getSize());
        log.info("✓ search_after 分页验证通过");
    }

    // ==================== 4. 嵌套逻辑 ====================

    @Test
    @Order(30)
    @DisplayName("逻辑：OR — 名字包含张或李")
    void testLogicOr() {
        QueryRequest req = (QueryRequest) translator.translate(
                nlParser.parse("名字包含张或李"), ctx("users"));
        QueryCondition cond = req.getQuery();
        assertEquals("or", cond.getLogic());
        assertFalse(cond.getConditions().isEmpty());
        for (QueryCondition c : cond.getConditions()) {
            assertEquals("like", c.getOp());
            assertEquals("name", c.getField());
        }
        log.info("✓ OR 逻辑验证通过");
    }

    @Test
    @Order(31)
    @DisplayName("逻辑：AND 多条件组合")
    void testLogicAndMultiple() {
        String nl = "状态等于active并且年龄大于等于18并且城市在北京、上海";
        QueryRequest req = (QueryRequest) translator.translate(nlParser.parse(nl), ctx("users"));
        QueryCondition cond = req.getQuery();
        assertEquals("and", cond.getLogic());
        assertTrue(cond.getConditions().size() >= 2);
        boolean hasStatus = false, hasAge = false, hasCity = false;
        for (QueryCondition c : cond.getConditions()) {
            if ("status".equals(c.getField()) && "eq".equals(c.getOp())) hasStatus = true;
            if ("age".equals(c.getField()) && "gte".equals(c.getOp())) hasAge = true;
            if ("city".equals(c.getField()) && "in".equals(c.getOp())) hasCity = true;
        }
        assertTrue(hasStatus, "应包含 status=active");
        assertTrue(hasAge, "应包含 age>=18");
        assertTrue(hasCity, "应包含 city IN [北京,上海]");
        log.info("✓ AND 多条件逻辑验证通过");
    }

    // ==================== 5. 日期范围 ====================

    @Test
    @Order(40)
    @DisplayName("日期范围 → QueryRequest.dateRange")
    void testDateRange() {
        String nl = "年龄大于18，时间范围2025-01-01到2025-12-31";
        QueryRequest req = (QueryRequest) translator.translate(nlParser.parse(nl), ctx("app_log"));
        assertNotNull(req.getDateRange(), "应包含 dateRange");
        assertNotNull(req.getDateRange().getFrom(), "dateRange.from 不应为空");
        assertNotNull(req.getDateRange().getTo(), "dateRange.to 不应为空");
        assertTrue(req.getDateRange().getFrom().startsWith("2025-01-01"), "from 应为 2025-01-01");
        assertTrue(req.getDateRange().getTo().startsWith("2025-12-31"), "to 应为 2025-12-31");
        log.info("✓ 日期范围验证通过: from={}, to={}", req.getDateRange().getFrom(), req.getDateRange().getTo());
    }

    // ==================== 6. 聚合类型覆盖 ====================

    @Test
    @Order(50)
    @DisplayName("聚合：sum / min / max 指标聚合")
    void testAggSumMinMax() {
        AggRequest sum = (AggRequest) translator.translate(
                nlParser.parse("按城市分组求和金额"), ctx("orders"));
        assertEquals("terms", sum.getAggs().get(0).getType());
        assertEquals("city", sum.getAggs().get(0).getField());
        assertEquals("sum", sum.getAggs().get(0).getAggs().get(0).getType());
        assertEquals("amount", sum.getAggs().get(0).getAggs().get(0).getField());

        AggRequest min = (AggRequest) translator.translate(
                nlParser.parse("按城市分组最小年龄"), ctx("users"));
        assertEquals("min", min.getAggs().get(0).getAggs().get(0).getType());

        AggRequest max = (AggRequest) translator.translate(
                nlParser.parse("按城市分组最大年龄"), ctx("users"));
        assertEquals("max", max.getAggs().get(0).getAggs().get(0).getType());

        log.info("✓ sum/min/max 聚合验证通过");
    }

    @Test
    @Order(51)
    @DisplayName("聚合：cardinality 去重计数")
    void testAggCardinality() {
        AggRequest req = (AggRequest) translator.translate(
                nlParser.parse("去重计数城市"), ctx("users"));
        assertNotNull(req.getAggs());
        assertEquals(1, req.getAggs().size());
        assertEquals("cardinality", req.getAggs().get(0).getType());
        assertEquals("city", req.getAggs().get(0).getField());
        log.info("✓ cardinality 聚合验证通过");
    }

    @Test
    @Order(52)
    @DisplayName("聚合：多个并行聚合")
    void testAggMultipleParallel() {
        String nl = "按城市分组，同时按创建时间每天统计";
        AggRequest req = (AggRequest) translator.translate(nlParser.parse(nl), ctx("orders"));
        assertEquals(2, req.getAggs().size());

        AggDefinition terms = req.getAggs().get(0);
        assertEquals("terms", terms.getType());
        assertEquals("city", terms.getField());

        AggDefinition dateHist = req.getAggs().get(1);
        assertEquals("date_histogram", dateHist.getType());
        assertEquals("createTime", dateHist.getField());
        assertEquals("1d", dateHist.getInterval());
        log.info("✓ 多个并行聚合验证通过");
    }

    @Test
    @Order(53)
    @DisplayName("聚合：带过滤条件的聚合")
    void testAggWithCondition() {
        String nl = "状态等于active，统计平均年龄";
        AggRequest req = (AggRequest) translator.translate(nlParser.parse(nl), ctx("users"));

        assertNotNull(req.getQuery());
        assertEquals("status", req.getQuery().getField());
        assertEquals("eq", req.getQuery().getOp());
        assertEquals("active", req.getQuery().getValue());

        assertEquals(1, req.getAggs().size());
        assertEquals("avg", req.getAggs().get(0).getType());
        assertEquals("age", req.getAggs().get(0).getField());
        log.info("✓ 带过滤条件的聚合验证通过");
    }

    @Test
    @Order(54)
    @DisplayName("聚合：terms + size + nested avg")
    void testAggTermsWithSizeAndNested() {
        String nl = "按城市分组前10个统计平均年龄";
        AggRequest req = (AggRequest) translator.translate(nlParser.parse(nl), ctx("users"));

        AggDefinition terms = req.getAggs().get(0);
        assertEquals("terms", terms.getType());
        assertEquals("city", terms.getField());
        assertEquals(10, terms.getSize());
        assertEquals(1, terms.getAggs().size());
        assertEquals("avg", terms.getAggs().get(0).getType());
        assertEquals("age", terms.getAggs().get(0).getField());
        log.info("✓ terms+size+nested avg 验证通过");
    }

    @Test
    @Order(55)
    @DisplayName("聚合：date_histogram 多种时间间隔")
    void testAggDateHistogramIntervals() {
        AggRequest hourly = (AggRequest) translator.translate(
                nlParser.parse("按创建时间每小时统计"), ctx("logs"));
        assertEquals("1h", hourly.getAggs().get(0).getInterval());

        AggRequest weekly = (AggRequest) translator.translate(
                nlParser.parse("按创建时间每周统计"), ctx("logs"));
        assertEquals("1w", weekly.getAggs().get(0).getInterval());

        AggRequest monthly = (AggRequest) translator.translate(
                nlParser.parse("按创建时间每月统计"), ctx("logs"));
        assertEquals("1M", monthly.getAggs().get(0).getInterval());

        log.info("✓ date_histogram 多种时间间隔验证通过");
    }

    @Test
    @Order(56)
    @DisplayName("聚合：完整场景 — 索引 + 条件 + 嵌套聚合 + 时间聚合")
    void testFullAggScenario() {
        String nl = "查询users索引，status等于active，" +
                "按city分组前10名计算age平均值，按createTime每天统计";
        AggRequest req = (AggRequest) translator.translate(nlParser.parse(nl), ctx(null));

        assertEquals("users", req.getIndex());
        assertNotNull(req.getQuery());
        assertEquals("status", req.getQuery().getField());
        assertEquals(2, req.getAggs().size());

        AggDefinition cityAgg = req.getAggs().get(0);
        assertEquals("terms", cityAgg.getType());
        assertEquals("city", cityAgg.getField());
        assertEquals(10, cityAgg.getSize());
        assertEquals("avg", cityAgg.getAggs().get(0).getType());
        assertEquals("age", cityAgg.getAggs().get(0).getField());

        AggDefinition dailyAgg = req.getAggs().get(1);
        assertEquals("date_histogram", dailyAgg.getType());
        assertEquals("createTime", dailyAgg.getField());
        assertEquals("1d", dailyAgg.getInterval());

        log.info("✓ 完整聚合场景验证通过");
    }

    // ==================== SimpleFieldBinder — 测试用字段绑定 ====================

    /**
     * 简单字段绑定器 — 用于测试
     * <p>
     * 生产环境应从配置文件读取（如 SimpleElasticsearchSearchProperties 的 field-mapping），
     * 此处硬编码仅用于测试验证。
     */
    static class SimpleFieldBinder implements FieldBinder {

        private static final Map<String, Map<String, String>> MAPPING = new HashMap<>();

        static {
            Map<String, String> users = new HashMap<>();
            users.put("年龄", "age");
            users.put("城市", "city");
            users.put("状态", "status");
            users.put("创建时间", "createTime");
            users.put("名字", "name");
            users.put("积分", "score");
            users.put("源IP", "sourceIp");
            users.put("邮箱", "email");
            users.put("金额", "amount");
            users.put("价格", "price");
            users.put("时间戳", "timestamp");
            MAPPING.put("users", users);
            MAPPING.put("user", users);
            MAPPING.put("user_profile", users);

            Map<String, String> orders = new HashMap<>();
            orders.put("金额", "amount");
            orders.put("状态", "status");
            orders.put("城市", "city");
            orders.put("创建时间", "createTime");
            MAPPING.put("orders", orders);

            Map<String, String> logs = new HashMap<>();
            logs.put("源IP", "sourceIp");
            logs.put("创建时间", "createTime");
            logs.put("状态", "status");
            MAPPING.put("logs", logs);

            Map<String, String> appLog = new HashMap<>();
            appLog.put("创建时间", "createTime");
            appLog.put("年龄", "age");
            MAPPING.put("app_log", appLog);
        }

        @Override
        public String bind(String fieldHint, String dataSource) {
            if (fieldHint == null) return null;
            Map<String, String> indexMapping = MAPPING.get(dataSource);
            if (indexMapping != null && indexMapping.containsKey(fieldHint)) {
                return indexMapping.get(fieldHint);
            }
            for (Map<String, String> mapping : MAPPING.values()) {
                if (mapping.containsKey(fieldHint)) {
                    return mapping.get(fieldHint);
                }
            }
            return fieldHint;
        }

        @Override
        public List<String> getAvailableFields(String dataSource) {
            Map<String, String> indexMapping = MAPPING.get(dataSource);
            if (indexMapping != null) {
                return new ArrayList<>(indexMapping.values());
            }
            return new ArrayList<>();
        }

        @Override
        public String getDataSourceType() {
            return "elasticsearch";
        }
    }
}
