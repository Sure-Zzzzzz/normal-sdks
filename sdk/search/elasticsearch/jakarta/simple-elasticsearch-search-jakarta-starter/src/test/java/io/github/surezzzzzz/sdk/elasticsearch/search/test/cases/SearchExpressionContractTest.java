package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionValidationRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.DefaultExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.TimeRangeEnd;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.SearchIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.CursorTokenCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.JacksonSearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 真实 parser、Visitor 和 DSL 编译器的契约；mock 只隔离未被声明为真实拓扑的外部边界。
 */
@Slf4j
class SearchExpressionContractTest {
    private final Instant anchor = Instant.parse("2024-05-15T12:00:00Z");
    private SimpleElasticsearchSearchProperties properties;
    private MappingManager mappings;
    private SearchEngine engine;
    private SearchIndexResolver indices;
    private DefaultSearchDslBuilder queries;
    private ExpressionService service;
    private Map<String, FieldMetadata> fields;

    static Stream<Arguments> relativePeriods() {
        return Stream.of(
                Arguments.of("今天", "2024-05-15T00:00:00Z", "2024-05-15T12:00:00Z"),
                Arguments.of("昨天", "2024-05-14T00:00:00Z", "2024-05-15T00:00:00Z"),
                Arguments.of("前天", "2024-05-13T00:00:00Z", "2024-05-14T00:00:00Z"),
                Arguments.of("本周", "2024-05-13T00:00:00Z", "2024-05-15T12:00:00Z"),
                Arguments.of("上周", "2024-05-06T00:00:00Z", "2024-05-13T00:00:00Z"),
                Arguments.of("本月", "2024-05-01T00:00:00Z", "2024-05-15T12:00:00Z"),
                Arguments.of("上月", "2024-04-01T00:00:00Z", "2024-05-01T00:00:00Z"),
                Arguments.of("本季度", "2024-04-01T00:00:00Z", "2024-05-15T12:00:00Z"),
                Arguments.of("上季度", "2024-01-01T00:00:00Z", "2024-04-01T00:00:00Z"),
                Arguments.of("今年", "2024-01-01T00:00:00Z", "2024-05-15T12:00:00Z"),
                Arguments.of("去年", "2023-01-01T00:00:00Z", "2024-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setup() {
        properties = new SimpleElasticsearchSearchProperties();
        SimpleElasticsearchSearchProperties.IndexConfig config = new SimpleElasticsearchSearchProperties.IndexConfig();
        config.setName("sample-record");
        config.setAlias("record");
        config.setDateField("occurredAt");
        config.getFieldMapping().put("amount", List.of("金额", "金额标签"));
        config.getFieldMapping().put("status", List.of("状态", "展示 状态"));
        SimpleElasticsearchSearchProperties.SensitiveFieldConfig forbidden = new SimpleElasticsearchSearchProperties.SensitiveFieldConfig();
        forbidden.setField("secret");
        forbidden.setStrategy("forbidden");
        config.getSensitiveFields().add(forbidden);
        properties.setIndices(List.of(config));
        SimpleElasticsearchRouteProperties routes = new SimpleElasticsearchRouteProperties();
        routes.setDefaultSource("primary");
        routes.getSources().put("primary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        RouteResolver resolver = new RouteResolver(routes, new RoutePatternMatcher());
        resolver.init();
        indices = new SearchIndexResolver(properties, resolver, routes);
        fields = new LinkedHashMap<>();
        for (String[] field : new String[][]{{"amount", "double"}, {"status", "keyword"}, {"active", "boolean"},
                {"occurredAt", "date"}, {"epoch", "long"}, {"secret", "keyword"}, {"details.status", "keyword"}}) {
            fields.put(field[0], new FieldMetadata(field[0], field[1], null, true, true, false, false, List.of()));
        }
        mappings = mock(MappingManager.class);
        when(mappings.fields(any())).thenReturn(fields);
        engine = mock(SearchEngine.class);
        ConditionExpressionParser parser = new ConditionExpressionParser(new ValueParser(List.of(
                new BooleanValueParseStrategy(), new NumberValueParseStrategy(), new TimeRangeValueParseStrategy(), new StringValueParseStrategy())));
        queries = new DefaultSearchDslBuilder(properties, new SensitiveFieldProcessor());
        service = new DefaultExpressionService(parser, properties, indices, mappings, queries, engine,
                new JacksonSearchPayloadCodec(), mock(CursorTokenCodec.class));
    }

    @Test
    void labelsOnlyResolveAstFieldsAndNeverReplaceValues() {
        QueryCondition translated = service.translate("金额=18 AND 状态='金额标签'", "record");
        assertEquals("amount", translated.getConditions().get(0).getField());
        assertEquals(18L, translated.getConditions().get(0).getValue());
        assertEquals("金额标签", translated.getConditions().get(1).getValue());
        assertEquals("status", service.translate("`展示 状态`='ready'", "record").getField());
        assertEquals("details.status", service.translate("details.status='ready'", "record").getField());
        assertEquals(true, service.translate("active='true'", "record").getValue());
    }

    @Test
    void patternTextPreservesNumericAndBooleanSpelling() {
        assertEquals("005", service.translate("status SUFFIX LIKE '005'", "record").getValue());
        assertEquals("TRUE", service.translate("status PREFIX LIKE 'TRUE'", "record").getValue());
        assertEquals("昨天", service.translate("status LIKE '昨天'", "record").getValue());
    }

    @Test
    void stringFieldsAndDocumentIdsPreserveScalarSpelling() {
        assertEquals("001", service.translate("status='001'", "record").getValue());
        assertEquals("TRUE", service.translate("status=TRUE", "record").getValue());
        assertEquals("001", service.translate("_id=001", "record").getValue());
        assertEquals(List.of("001", "TRUE"), service.translate("_id IN ('001','TRUE')", "record").getValues());
        assertEquals(List.of("001", "TRUE"), service.translate("status IN ('001','TRUE')", "record").getValues());
        assertEquals(1L, service.translate("amount='001'", "record").getValue());
        assertEquals(true, service.translate("active=TRUE", "record").getValue());
    }

    @Test
    void parserIsConsumedAsPublishedJarWithMatchingRuntime() {
        assertTrue(ConditionExpressionParser.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"));
        assertEquals("4.10.1", org.antlr.v4.runtime.RuntimeMetaData.getRuntimeVersion());
    }

    @Test
    void booleanNotUsesMustNotRatherThanReversingComparisons() {
        QueryCondition translated = service.translate("NOT (amount>3 OR status='ready')", "record");
        assertEquals("not", translated.getLogic());
        assertEquals(1, translated.getConditions().size());
        Map<String, Object> compiled = queries.query(translated, indices.resolve("record", null), fields);
        assertTrue(((Map<?, ?>) compiled.get("bool")).containsKey("must_not"));
        assertEquals("not", service.translate("NOT NOT amount>3", "record").getConditions().get(0).getLogic());
        assertThrows(SimpleElasticsearchSearchException.class, () -> queries.query(
                QueryCondition.builder().logic("not").conditions(List.of(translated, translated)).build(), indices.resolve("record", null), fields));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "amount=", "amount IN ()", "amount=999999999999999999999999", "unknown=1", "secret='hidden'",
            "status=昨天", "amount IN (今天)", "amount=近7天"})
    void invalidAndSensitiveInputsDoNotLeakOrInvokeEngine(String expression) {
        SimpleElasticsearchSearchException error = assertThrows(SimpleElasticsearchSearchException.class,
                () -> service.translate(expression, "record"));
        assertEquals(400, error.getStatus());
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("hidden"));
        verifyNoInteractions(engine);
    }

    @Test
    void everyParserResourceLimitIsAppliedAndContextsAreSafe() {
        properties.getExpression().setMaxLength(5);
        assertThrows(SimpleElasticsearchSearchException.class, () -> service.translate("amount=18", "record"));
        properties.getExpression().setMaxLength(16384);
        properties.getExpression().setMaxTokens(2);
        assertThrows(SimpleElasticsearchSearchException.class, () -> service.translate("amount=18", "record"));
        properties.getExpression().setMaxTokens(4096);
        properties.getQueryLimits().setMaxDepth(2);
        assertThrows(SimpleElasticsearchSearchException.class, () -> service.translate("((amount=18))", "record"));
        properties.getQueryLimits().setMaxDepth(32);
        properties.getQueryLimits().setMaxNodes(2);
        assertThrows(SimpleElasticsearchSearchException.class, () -> service.translate("amount=1 OR amount=2", "record"));
        properties.getQueryLimits().setMaxNodes(1000);
        assertThrows(SimpleElasticsearchSearchException.class, () -> service.translate("occurredAt=昨天", "record", null, "invalid", anchor));
        assertThrows(SimpleElasticsearchSearchException.class, () -> service.query(ExpressionQueryRequest.builder().index("record")
                .expression("amount=1").pagination(PaginationInfo.builder().type("invalid").build()).build()));
    }

    @Test
    void validationDoesNotTurnExternalFailuresIntoInvalidInput() {
        assertTrue(service.validate(ExpressionValidationRequest.builder().index("record").expression("amount=18").build()).isValid());
        assertFalse(service.validate(ExpressionValidationRequest.builder().index("record").expression("secret='hidden'").build()).isValid());
        assertFalse(service.validate(ExpressionValidationRequest.builder().index("record").expression("status='sensitive-sentinel").build()).getMessage().contains("sensitive-sentinel"));
        when(mappings.fields(any())).thenThrow(new SimpleElasticsearchSearchException(
                ErrorCode.REQUEST_INVALID, "private-sentinel", 400));
        ExpressionValidationResponse safe = service.validate(
                ExpressionValidationRequest.builder().index("record").expression("amount=18").build());
        assertFalse(safe.isValid());
        assertEquals(ErrorMessage.REQUEST_INVALID, safe.getMessage());
        doThrow(SearchValidationHelper.protocol()).when(mappings).fields(any());
        assertThrows(SimpleElasticsearchSearchException.class,
                () -> service.validate(ExpressionValidationRequest.builder().index("record").expression("amount=18").build()));
        verifyNoInteractions(engine);
    }

    @ParameterizedTest
    @MethodSource("relativePeriods")
    void calendarKeywordsUseExactHalfOpenPeriods(String keyword, String from, String to) {
        assertPeriod(service.translate("occurredAt=" + keyword, "record", null, null, anchor), from, to);
    }

    @ParameterizedTest
    @EnumSource(value = TimeRange.class, mode = EnumSource.Mode.MATCH_ALL, names = "LAST_[0-9].*")
    void rollingKeywordsUseTheirPublishedCalendarUnit(TimeRange range) {
        ZonedDateTime end = anchor.atZone(ZoneOffset.UTC);
        assertPeriod(service.translate("occurredAt=" + range.getKeyword(), "record", null, null, anchor),
                end.minus(range.getAmount(), range.getUnit()).toInstant().toString(), anchor.toString());
    }

    @Test
    void midnightModeDstAndEpochSecondsStayExplicit() {
        assertPeriod(service.translate("occurredAt=近7天", "record", TimeRangeEnd.TODAY_START, null, anchor),
                "2024-05-08T00:00:00Z", "2024-05-15T00:00:00Z");
        assertPeriod(service.translate("occurredAt=昨天", "record", null, "America/New_York", Instant.parse("2024-03-11T12:00:00Z")),
                "2024-03-10T05:00:00Z", "2024-03-11T04:00:00Z");
        QueryCondition epoch = service.translate("epoch=昨天", "record", null, null, anchor);
        assertEquals(Instant.parse("2024-05-14T00:00:00Z").getEpochSecond(), epoch.getConditions().get(0).getValue());
        assertEquals(Instant.parse("2024-05-15T00:00:00Z").getEpochSecond(), epoch.getConditions().get(1).getValue());
    }

    @Test
    void timeComparatorsReferToCorrectPeriodBoundary() {
        Map<String, String> operators = Map.of(">", "gte", ">=", "gte", "<", "lt", "<=", "lt");
        for (Map.Entry<String, String> item : operators.entrySet()) {
            QueryCondition condition = service.translate("occurredAt" + item.getKey() + "昨天", "record", null, null, anchor);
            assertEquals(item.getValue(), condition.getOp());
            assertEquals(item.getKey().equals(">") || item.getKey().equals("<=")
                    ? "2024-05-15T00:00:00Z" : "2024-05-14T00:00:00Z", condition.getValue());
        }
        assertEquals("not", service.translate("occurredAt!=昨天", "record", null, null, anchor).getLogic());
    }

    @Test
    void ambiguousLabelsAndParserLimitsRejectConfiguration() {
        SimpleElasticsearchSearchProperties.IndexConfig config = properties.getIndices().get(0);
        config.getFieldMapping().put("status", List.of("金额"));
        assertThrows(SimpleElasticsearchSearchException.class, () -> rebuildIndices());
        config.getFieldMapping().put("status", List.of("amount"));
        assertThrows(SimpleElasticsearchSearchException.class, () -> rebuildIndices());
        config.getFieldMapping().put("status", List.of("未注册真实字段"));
        fields.put("未注册真实字段", new FieldMetadata("未注册真实字段", "keyword", null, true, true, false, false, List.of()));
        assertThrows(SimpleElasticsearchSearchException.class, () -> service.translate("未注册真实字段='ready'", "record"));
        properties.getExpression().setMaxTokens(0);
        assertThrows(SimpleElasticsearchSearchException.class, () -> rebuildIndices());
    }

    private SearchIndexResolver rebuildIndices() {
        SimpleElasticsearchRouteProperties routes = new SimpleElasticsearchRouteProperties();
        routes.setDefaultSource("primary");
        routes.getSources().put("primary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        RouteResolver resolver = new RouteResolver(routes, new RoutePatternMatcher());
        resolver.init();
        return new SearchIndexResolver(properties, resolver, routes);
    }

    private void assertPeriod(QueryCondition condition, String from, String to) {
        assertEquals("and", condition.getLogic());
        assertEquals(List.of("gte", "lt"), condition.getConditions().stream().map(QueryCondition::getOp).collect(java.util.stream.Collectors.toList()));
        assertEquals(from, condition.getConditions().get(0).getValue());
        assertEquals(to, condition.getConditions().get(1).getValue());
    }
}
