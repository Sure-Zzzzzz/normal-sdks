package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.DefaultAggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.SearchIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchRestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 对齐旧线的结构化查询、日期目标、字段合并和聚合边界，断言遵循 Jakarta 契约。
 */
@Slf4j
class SearchMigrationTest {
    private SimpleElasticsearchSearchProperties properties;
    private SimpleElasticsearchSearchProperties.IndexConfig config;
    private SimpleElasticsearchRouteProperties routes;
    private SearchIndexResolver resolver;
    private ResolvedIndex target;
    private DefaultSearchDslBuilder queries;
    private Map<String, FieldMetadata> fields;

    @BeforeEach
    void setup() {
        properties = new SimpleElasticsearchSearchProperties();
        config = new SimpleElasticsearchSearchProperties.IndexConfig();
        config.setName("sample-*");
        config.setAlias("sample");
        config.setDateField("occurredAt");
        properties.setIndices(Collections.singletonList(config));
        routes = new SimpleElasticsearchRouteProperties();
        routes.setDefaultSource("primary");
        routes.getSources().put("primary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        rebuild();
        target = resolver.resolve("sample", null);
        queries = new DefaultSearchDslBuilder(properties, new SensitiveFieldProcessor());
        fields = new LinkedHashMap<>();
        for (String[] pair : new String[][]{{"status", "keyword"}, {"amount", "integer"}, {"occurredAt", "date"}, {"text", "text"}})
            fields.put(pair[0], metadata(pair[0], pair[1], null, true, !pair[1].equals("text"), false, false));
    }

    private void rebuild() {
        RouteResolver route = new RouteResolver(routes, new RoutePatternMatcher());
        route.init();
        resolver = new SearchIndexResolver(properties, route, routes);
    }

    private FieldMetadata metadata(String name, String type, String keyword, boolean indexed, boolean docValues, boolean conflict, boolean nested) {
        return new FieldMetadata(name, type, keyword, indexed, docValues, conflict, nested, Collections.emptyList());
    }

    private QueryCondition leaf(String field, String op, Object value) {
        QueryCondition.QueryConditionBuilder builder = QueryCondition.builder().field(field).op(op);
        if (value instanceof List) builder.values(new ArrayList<>((List<?>) value));
        else builder.value(value);
        return builder.build();
    }

    private Map<String, Object> dsl(QueryCondition condition) {
        return queries.query(condition, target, fields);
    }

    private QueryRequest.DateRange range(String from, String to) {
        return QueryRequest.DateRange.builder().from(from).to(to).build();
    }

    @Test
    void mixedAndOrKeepsIdAndNormalFieldPredicates() {
        QueryCondition either = QueryCondition.builder().logic("or").conditions(Arrays.asList(leaf("_id", "eq", "row1"), leaf("status", "eq", "ready"))).build();
        Map<String, Object> expectedOr = Map.of("bool", Map.of("should", Arrays.asList(Map.of("ids", Map.of("values", Collections.singletonList("row1"))), Map.of("term", Map.of("status", "ready"))), "minimum_should_match", 1));
        assertEquals(Map.of("bool", Map.of("filter", Arrays.asList(expectedOr, Map.of("range", Map.of("amount", Map.of("gt", 1)))))),
                dsl(QueryCondition.builder().logic("and").conditions(Arrays.asList(either, leaf("amount", "gt", 1))).build()));
    }

    @Test
    void textEqUsesPhraseWhileKeywordSubfieldUsesExactMatch() {
        assertEquals(Map.of("match_phrase", Map.of("text", "sample phrase")), dsl(leaf("text", "eq", "sample phrase")));
        fields.put("text", metadata("text", "text", "text.keyword", true, false, false, false));
        fields.put("text.keyword", metadata("text.keyword", "keyword", null, true, true, false, false));
        assertEquals(Map.of("term", Map.of("text.keyword", "sample phrase")), dsl(leaf("text", "eq", "sample phrase")));
        assertEquals(Map.of("terms", Map.of("text.keyword", Arrays.asList("a", "b"))), dsl(leaf("text", "in", Arrays.asList("a", "b"))));
        assertEquals("text.keyword", queries.field("text", target, fields, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"in", "not_in", "like", "not_like", "prefix", "suffix", "regex"})
    void pureTextRejectsUnsupportedExactAndPatternOperators(String op) {
        Object value = op.contains("in") ? Collections.singletonList("a") : "a";
        assertThrows(SimpleElasticsearchSearchException.class, () -> dsl(leaf("text", op, value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"conflict", "nested", "unindexed", "object", "missing"})
    void unusableFieldsReject(String reason) {
        if (reason.equals("missing")) fields.remove("status");
        else
            fields.put("status", metadata("status", reason.equals("object") ? "object" : "keyword", null, !reason.equals("unindexed"), true, reason.equals("conflict"), reason.equals("nested")));
        assertThrows(SimpleElasticsearchSearchException.class, () -> dsl(leaf("status", "eq", "ready")));
    }

    @Test
    void keywordCapabilityIsValidatedIndependently() {
        fields.put("text", metadata("text", "text", "text.keyword", true, false, false, false));
        assertThrows(SimpleElasticsearchSearchException.class, () -> dsl(leaf("text", "eq", "a")));
        fields.put("text.keyword", metadata("text.keyword", "keyword", null, false, false, false, false));
        assertThrows(SimpleElasticsearchSearchException.class, () -> dsl(leaf("text", "eq", "a")));
        assertThrows(SimpleElasticsearchSearchException.class, () -> queries.field("text", target, fields, true));
    }

    @Test
    void invalidConditionShapesAndLimitsReject() {
        List<QueryCondition> invalid = Arrays.asList(leaf("status", "unknown", "x"), leaf("status", "eq", null), leaf("status", "in", Collections.emptyList()),
                leaf("amount", "between", Collections.singletonList(1)), leaf("amount", "eq", Double.NaN), leaf("status", "eq", Map.of("x", 1)),
                leaf("_id", "gt", 1), leaf("status", "gt", 1), QueryCondition.builder().logic("xor").conditions(Collections.singletonList(leaf("status", "eq", "x"))).build(),
                QueryCondition.builder().field("status").logic("and").conditions(Collections.singletonList(leaf("status", "eq", "x"))).build());
        for (QueryCondition condition : invalid)
            assertThrows(SimpleElasticsearchSearchException.class, () -> dsl(condition));
        properties.getQueryLimits().setMaxNodes(1);
        assertThrows(SimpleElasticsearchSearchException.class, () -> dsl(QueryCondition.builder().conditions(Collections.singletonList(leaf("status", "eq", "x"))).build()));
        properties.getQueryLimits().setMaxNodes(100);
        properties.getQueryLimits().setMaxDepth(1);
        QueryCondition nested = QueryCondition.builder().conditions(Collections.singletonList(QueryCondition.builder().conditions(Collections.singletonList(leaf("status", "eq", "x"))).build())).build();
        assertThrows(SimpleElasticsearchSearchException.class, () -> dsl(nested));
    }

    @Test
    void dailyRangeCrossesLeapDayAndDateOnlyEndIsInclusive() {
        config.setDateSplit(true);
        rebuild();
        ResolvedIndex resolved = resolver.resolve("sample", range("2024-02-28", "2024-03-01"));
        assertArrayEquals(new String[]{"sample-2024.02.28", "sample-2024.02.29", "sample-2024.03.01"}, resolved.getIndices());
        assertEquals("2024-03-02T00:00:00Z", resolved.getTo());
        assertEquals(0, resolved.getDowngradeLevel());
    }

    @ParameterizedTest
    @CsvSource({"2,2024-01-01,2024-02-02,1,sample-2024.01.*,sample-2024.02.*", "2,2023-01-01,2024-12-31,2,sample-2023.*,sample-2024.*"})
    void dailyRangeDowngradesToMonthsOrYears(int limit, String from, String to, int level, String first, String last) {
        config.setDateSplit(true);
        properties.getQueryLimits().setMaxIndices(limit);
        rebuild();
        ResolvedIndex resolved = resolver.resolve("sample", range(from, to));
        assertEquals(level, resolved.getDowngradeLevel());
        assertArrayEquals(new String[]{first, last}, resolved.getIndices());
        assertNotNull(resolved.getFrom());
        assertNotNull(resolved.getTo());
    }

    @Test
    void fullWildcardDowngradeRequiresOptInAndStrictDateFilter() {
        config.setDateSplit(true);
        properties.getQueryLimits().setMaxIndices(1);
        rebuild();
        assertThrows(SimpleElasticsearchSearchException.class, () -> resolver.resolve("sample", range("2020-01-01", "2024-12-31")));
        properties.getQueryLimits().setAllowFullScan(true);
        ResolvedIndex resolved = resolver.resolve("sample", range("2020-01-01", "2024-12-31"));
        assertEquals(3, resolved.getDowngradeLevel());
        assertArrayEquals(new String[]{"sample-*"}, resolved.getIndices());
        properties.getQueryLimits().setStrictDateFilter(false);
        assertThrows(SimpleElasticsearchSearchException.class, () -> resolver.resolve("sample", range("2020-01-01", "2024-12-31")));
    }

    @ParameterizedTest
    @CsvSource({"yyyy-MM,2024-01-10,2024-02-10,sample-2024-01,sample-2024-02", "yyyy,2023-12-31,2024-01-01,sample-2023,sample-2024"})
    void monthAndYearPatternsDeduplicateTargets(String pattern, String from, String to, String first, String last) {
        config.setDateSplit(true);
        config.setDatePattern(pattern);
        rebuild();
        assertArrayEquals(new String[]{first, last}, resolver.resolve("sample", range(from, to)).getIndices());
    }

    @Test
    void timeZonePhysicalTargetAndResumeKeepFrozenRange() {
        config.setDateSplit(true);
        config.setZoneId("Asia/Shanghai");
        rebuild();
        ResolvedIndex resolved = resolver.resolve("sample", range("2024-01-01", "2024-01-01"));
        assertEquals("2023-12-31T16:00:00Z", resolved.getFrom());
        assertEquals("2024-01-01T16:00:00Z", resolved.getTo());
        assertArrayEquals(new String[]{"sample-2024.01.01"}, resolved.getIndices());
        String[] frozen = resolved.getIndices().clone();
        ResolvedIndex resumed = resolver.resume("sample", frozen, "primary", resolved.getFrom(), resolved.getTo(), 0);
        frozen[0] = "other";
        assertEquals("sample-2024.01.01", resumed.getIndices()[0]);
        assertEquals(resolved.getFrom(), resumed.getFrom());
        assertThrows(SimpleElasticsearchSearchException.class, () -> resolver.resume("sample", new String[]{"other"}, "primary", null, null, 0));
        assertThrows(SimpleElasticsearchSearchException.class, () -> resolver.resume("sample", resolved.getIndices(), "secondary", null, null, 0));
        assertArrayEquals(new String[]{"sample-2023.12.01"}, resolver.resolve("sample-2023.12.01", null).getIndices());
    }

    @Test
    void strictDatePredicateUsesExclusiveInstantEndAndRejectsWrongType() {
        target = resolver.resolve("sample", range("2024-01-01", "2024-01-02T00:00:00Z"));
        assertEquals(Map.of("bool", Map.of("filter", Arrays.asList(Map.of("match_all", Collections.emptyMap()), Map.of("range", Map.of("occurredAt", Map.of("gte", "2024-01-01T00:00:00Z", "lt", "2024-01-02T00:00:00Z", "format", "strict_date_optional_time_nanos")))))), dsl(null));
        fields.put("occurredAt", metadata("occurredAt", "long", null, true, true, false, false));
        assertThrows(SimpleElasticsearchSearchException.class, () -> dsl(null));
    }

    @Test
    void defaultDateRangeAndInvalidRangesAreExplicit() {
        config.setDateSplit(true);
        properties.getQueryLimits().setDefaultDateRange(null);
        rebuild();
        assertThrows(SimpleElasticsearchSearchException.class, () -> resolver.resolve("sample", null));
        properties.getQueryLimits().setDefaultDateRange("1d");
        ResolvedIndex resolved = resolver.resolve("sample", null);
        assertEquals(86400000, java.time.Duration.between(java.time.Instant.parse(resolved.getFrom()), java.time.Instant.parse(resolved.getTo())).toMillis());
        for (QueryRequest.DateRange invalid : Arrays.asList(range("bad", "2024-01-01"), range("2024-01-03", "2024-01-01"), range(null, "2024-01-01")))
            assertThrows(SimpleElasticsearchSearchException.class, () -> resolver.resolve("sample", invalid));
    }

    @Test
    void multiSourceWildcardOwnershipMustBeProvable() {
        routes.getSources().put("secondary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        SimpleElasticsearchRouteProperties.RouteRule primary = new SimpleElasticsearchRouteProperties.RouteRule();
        primary.setPattern("sample-*");
        primary.setType("wildcard");
        primary.setDatasource("primary");
        SimpleElasticsearchRouteProperties.RouteRule secondary = new SimpleElasticsearchRouteProperties.RouteRule();
        secondary.setPattern("other-*");
        secondary.setType("wildcard");
        secondary.setDatasource("secondary");
        routes.setRules(Arrays.asList(primary, secondary));
        rebuild();
        assertEquals("primary", resolver.resolve("sample", null).getDatasource());
        assertThrows(SimpleElasticsearchSearchException.class, () -> resolver.datasource("sample-a", "other-a"));
        secondary.setPattern("sample-private-*");
        assertThrows(SimpleElasticsearchSearchException.class, this::rebuild);
    }

    @Test
    void mappingMergeKeepsUnionAndFlagsConflictsInsteadOfNewestWins() {
        SearchRestHelper rest = mock(SearchRestHelper.class);
        when(rest.perform(anyString(), anyString(), anyString(), anyMap(), isNull())).thenReturn(Map.of(
                "sample-1", Map.of("mappings", Map.of("properties", Map.of("amount", Map.of("type", "integer"), "text", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword")))))),
                "sample-2", Map.of("mappings", Map.of("properties", Map.of("amount", Map.of("type", "keyword"), "extra", Map.of("type", "keyword"))))));
        MappingManager manager = new MappingManager(rest, properties, resolver, event -> {
        });
        Map<String, FieldMetadata> merged = manager.fields(target);
        assertEquals(new TreeSet<>(Arrays.asList("amount", "text", "text.keyword", "extra")), merged.keySet());
        assertTrue(merged.get("amount").isConflict());
        assertEquals("text.keyword", merged.get("text").getKeywordField());
        assertThrows(UnsupportedOperationException.class, () -> merged.clear());
        assertSame(merged, manager.fields(target));
        verify(rest, times(1)).perform(anyString(), anyString(), anyString(), anyMap(), isNull());
        when(rest.perform(anyString(), anyString(), anyString(), anyMap(), isNull())).thenThrow(new IllegalStateException("受控失败"));
        assertThrows(IllegalStateException.class, () -> manager.refresh(target));
        assertSame(merged, manager.fields(target));
    }

    @Test
    void mappingCacheSeparatesTargetsAndEvictsWithinBound() {
        SearchRestHelper rest = mock(SearchRestHelper.class);
        when(rest.perform(anyString(), anyString(), anyString(), anyMap(), isNull())).thenReturn(Map.of("sample-1", Map.of("mappings", Map.of("properties", Map.of("amount", Map.of("type", "integer"))))));
        properties.getQueryLimits().setMappingCacheSize(1);
        MappingManager manager = new MappingManager(rest, properties, resolver, event -> {
            throw new IllegalStateException("监听器失败");
        });
        ResolvedIndex first = resolver.resolve("sample-1", null), second = resolver.resolve("sample-2", null);
        manager.fields(first);
        manager.fields(second);
        manager.fields(first);
        verify(rest, times(3)).perform(anyString(), anyString(), anyString(), anyMap(), isNull());
        config.setCacheMapping(false);
        manager.fields(second);
        manager.fields(second);
        verify(rest, times(5)).perform(anyString(), anyString(), anyString(), anyMap(), isNull());
    }

    @Test
    void scheduledMappingRefreshContinuesAfterOneFailureAndLazyLoadDoesNotCallEs() {
        SearchRestHelper rest = mock(SearchRestHelper.class);
        SimpleElasticsearchSearchProperties.IndexConfig second = new SimpleElasticsearchSearchProperties.IndexConfig();
        second.setName("other-record");
        config.setLazyLoad(true);
        second.setLazyLoad(true);
        properties.setIndices(Arrays.asList(config, second));
        rebuild();
        when(rest.perform(eq("primary"), eq("GET"), eq("/sample-*/_mapping"), anyMap(), isNull())).thenThrow(new IllegalStateException("受控刷新失败"));
        when(rest.perform(eq("primary"), eq("GET"), eq("/other-record/_mapping"), anyMap(), isNull())).thenReturn(Map.of("other-record", Map.of("mappings", Map.of("properties", Map.of("amount", Map.of("type", "integer"))))));
        List<Object> events = new ArrayList<>();
        MappingManager manager = new MappingManager(rest, properties, resolver, events::add);
        manager.initialize();
        verifyNoInteractions(rest);
        manager.refreshAll();
        assertEquals(2, events.size());
        assertFalse(((io.github.surezzzzzz.sdk.elasticsearch.search.event.MappingRefreshEvent) events.get(0)).isSuccess());
        assertTrue(((io.github.surezzzzzz.sdk.elasticsearch.search.event.MappingRefreshEvent) events.get(1)).isSuccess());
        verify(rest).perform(eq("primary"), eq("GET"), eq("/other-record/_mapping"), anyMap(), isNull());
    }

    private DefaultAggregationDslBuilder aggs() {
        return new DefaultAggregationDslBuilder(queries, new SensitiveFieldProcessor(), properties);
    }

    private void rejects(AggDefinition value) {
        assertThrows(SimpleElasticsearchSearchException.class, () -> aggs().build(Collections.singletonList(value), null, target, fields));
    }

    @ParameterizedTest
    @ValueSource(strings = {"range", "date_range", "ip_range", "filter", "filters", "missing", "sum"})
    void compositeRejectsUnsupportedTypes(String type) {
        rejects(AggDefinition.builder().name("sampleAgg").type(type).field("amount").composite(true).build());
    }

    @Test
    void aggregationInputsAndAfterKeysAreValidated() {
        rejects(AggDefinition.builder().name("x").type("filter").query(leaf("amount", "eq", null)).build());
        rejects(AggDefinition.builder().name("x").type("filters").build());
        rejects(AggDefinition.builder().name("x").type("range").field("amount").build());
        rejects(AggDefinition.builder().name("x").type("percentile_ranks").field("amount").build());
        rejects(AggDefinition.builder().name("x").type("percentiles").field("amount").percents(Arrays.asList(-1.0, 101.0)).build());
        rejects(AggDefinition.builder().name("x").type("histogram").field("amount").interval("0").build());
        rejects(AggDefinition.builder().name("x").type("sum").field("status").build());
        AggDefinition composite = AggDefinition.builder().name("groups").type("terms").field("status").composite(true).build();
        assertThrows(SimpleElasticsearchSearchException.class, () -> aggs().build(Collections.singletonList(composite), Map.of("unknown", Map.of("status", "x")), target, fields));
        assertThrows(SimpleElasticsearchSearchException.class, () -> aggs().build(Collections.singletonList(composite), Map.of("groups", Map.of("wrong", "x")), target, fields));
        Map<?, ?> compiled = (Map<?, ?>) aggs().build(Collections.singletonList(composite), Map.of("groups", Map.of("status", "ready")), target, fields).get("groups");
        assertEquals(Map.of("status", "ready"), ((Map<?, ?>) compiled.get("composite")).get("after"));
    }

    @Test
    void pipelineRejectsMissingScriptUnknownSiblingAndCompositeParent() {
        AggDefinition parent = AggDefinition.builder().name("groups").type("terms").field("status")
                .aggs(Collections.singletonList(AggDefinition.builder().name("amountSum").type("sum").field("amount").build())).build();
        parent.setPipelineAggs(Collections.singletonList(PipelineAggDefinition.builder().name("keep").type("bucket_selector").bucketsPath(Map.of("v", "amountSum")).build()));
        rejects(parent);
        parent.getPipelineAggs().get(0).setScript("params.v > 1");
        parent.getPipelineAggs().get(0).setBucketsPath(Map.of("v", "unknown"));
        rejects(parent);
        parent.getPipelineAggs().get(0).setBucketsPath(Map.of("v", "amountSum"));
        assertTrue(aggs().build(Collections.singletonList(parent), null, target, fields).containsKey("groups"));
        parent.setComposite(true);
        rejects(parent);
    }
}
