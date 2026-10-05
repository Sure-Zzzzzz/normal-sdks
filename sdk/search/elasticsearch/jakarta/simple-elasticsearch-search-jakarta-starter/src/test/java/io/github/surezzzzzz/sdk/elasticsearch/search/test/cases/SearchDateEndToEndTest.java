package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.AggregationResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.DefaultAggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.DefaultSearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.SearchIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.AesGcmCursorTokenCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchRestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.support.SearchFixture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 独占日期索引验证降级后的真实 query/count/agg/scroll 与元数据并集，不靠 DSL 快照代替结果。
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchDateEndToEndTest {
    private final Map<String, String> owned = new LinkedHashMap<>();
    @Autowired
    private SearchFixture fixture;
    @Autowired
    private SearchPayloadCodec codec;
    @Autowired
    private SearchRestHelper rest;
    @Autowired
    private RouteResolver routes;
    @Autowired
    private SimpleElasticsearchRouteProperties routeProperties;

    @AfterEach
    void cleanup() throws Exception {
        for (Map.Entry<String, String> entry : owned.entrySet()) fixture.delete(entry.getValue(), entry.getKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void monthlyDowngradePreservesDateFilterAcrossEveryReadShape(String source) throws Exception {
        String prefix = (source.equals("primary") ? "test-js7-date-" : "test-js8-date-") + UUID.randomUUID() + "--";
        String[] dates = {"2024.01.01", "2024.01.31", "2024.02.01"};
        String[] instants = {"2024-01-01T12:00:00Z", "2024-01-31T12:00:00Z", "2024-02-01T12:00:00Z"};
        for (int i = 0; i < dates.length; i++) {
            String index = prefix + dates[i];
            Map<String, Object> definitions = new LinkedHashMap<>(Map.of("occurredAt", Map.of("type", "date"), "amount", Map.of("type", "integer"), "rank", Map.of("type", "integer")));
            if (i == 2) definitions.put("extra", Map.of("type", "keyword"));
            fixture.request(source, "PUT", "/" + index, codec.encode(Map.of("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0), "mappings", Map.of("properties", definitions))));
            owned.put(index, source);
            fixture.request(source, "PUT", "/" + index + "/_doc/row" + i, codec.encode(Map.of("occurredAt", instants[i], "amount", i + 1, "rank", i + 1)));
            fixture.request(source, "POST", "/" + index + "/_refresh", null);
        }
        SimpleElasticsearchSearchProperties properties = new SimpleElasticsearchSearchProperties();
        SimpleElasticsearchSearchProperties.IndexConfig config = new SimpleElasticsearchSearchProperties.IndexConfig();
        config.setName(prefix + "*");
        config.setAlias("dates");
        config.setDateSplit(true);
        config.setDateField("occurredAt");
        config.setTiebreakerField("rank");
        properties.setIndices(Collections.singletonList(config));
        properties.getQueryLimits().setMaxIndices(2);
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        properties.getCursor().setEncryptionKey(Base64.getEncoder().encodeToString(key));
        SearchIndexResolver indices = new SearchIndexResolver(properties, routes, routeProperties);
        MappingManager mappings = new MappingManager(rest, properties, indices, event -> {
        });
        SensitiveFieldProcessor sensitive = new SensitiveFieldProcessor();
        SearchDslBuilder queries = new DefaultSearchDslBuilder(properties, sensitive);
        SearchEngine engine = new DefaultSearchEngine(properties, codec, rest, indices, mappings, sensitive, queries,
                new DefaultAggregationDslBuilder(queries, sensitive, properties), new DefaultSearchResponseParser(sensitive, properties), new AggregationResponseParser(),
                new AesGcmCursorTokenCodec(properties, codec), event -> {
        });
        QueryRequest.DateRange range = QueryRequest.DateRange.builder().from("2024-01-20").to("2024-02-10").build();
        io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex target = indices.resolve("dates", range);
        assertEquals(1, target.getDowngradeLevel());
        assertEquals(source, target.getDatasource());
        assertArrayEquals(new String[]{prefix + "2024.01.*", prefix + "2024.02.*"}, target.getIndices());
        assertTrue(mappings.fields(target).containsKey("extra"));
        QueryRequest request = QueryRequest.builder().index("dates").dateRange(range).fields(Collections.singletonList("amount"))
                .pagination(PaginationInfo.builder().type("offset").page(2).size(1).sort(Collections.singletonList(PaginationInfo.SortField.builder().field("rank").order("asc").build())).build()).build();
        QueryResponse page = engine.query(request);
        assertEquals(2, page.getTotal());
        assertEquals(3, page.getItems().get(0).get("amount"));
        assertFalse(page.getItems().get(0).containsKey("occurredAt"));
        assertEquals(2, engine.count(request).getTotal());
        AggResponse aggregate = engine.aggregate(AggRequest.builder().index("dates").dateRange(range)
                .aggs(Collections.singletonList(AggDefinition.builder().name("amountSum").type("sum").field("amount").build())).build());
        assertEquals(5.0, ((Number) aggregate.getAggregations().get("amountSum")).doubleValue());
        request.setPagination(PaginationInfo.builder().type("scroll").size(1).build());
        Set<Object> seen = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            QueryResponse result = engine.query(request);
            for (Map<String, Object> row : result.getItems()) assertTrue(seen.add(row.get("amount")));
            if (!Boolean.TRUE.equals(result.getPagination().getHasMore())) break;
            request.getPagination().setScrollId(result.getPagination().getScrollId());
        }
        assertEquals(new HashSet<>(Arrays.asList(2, 3)), seen);
    }
}
