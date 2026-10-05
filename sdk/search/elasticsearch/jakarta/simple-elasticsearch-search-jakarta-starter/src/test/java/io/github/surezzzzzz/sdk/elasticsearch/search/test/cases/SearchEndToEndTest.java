package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.support.SearchFixture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询合同在真实 ES7、ES8 上逐项核验，HTTP 访问当前测试宿主的随机端口。
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchEndToEndTest {
    private final Map<String, String> ownedIndices = new LinkedHashMap<>();
    private final String run = UUID.randomUUID().toString();
    @Autowired
    private SearchEngine engine;
    @Autowired
    private SearchFixture fixture;
    @Autowired
    private SearchPayloadCodec codec;
    @Autowired
    private TestRestTemplate http;

    static Stream<Arguments> operators() {
        List<Object[]> cases = List.of(
                new Object[]{"eq", "status", "ready", 2L}, new Object[]{"ne", "status", "ready", 3L},
                new Object[]{"gt", "amount", 3, 2L}, new Object[]{"gte", "amount", 3, 3L},
                new Object[]{"lt", "amount", 3, 2L}, new Object[]{"lte", "amount", 3, 3L},
                new Object[]{"in", "status", List.of("ready"), 2L}, new Object[]{"not_in", "status", List.of("ready"), 3L},
                new Object[]{"between", "amount", List.of(2, 4), 3L},
                new Object[]{"like", "name", "*ha*", 5L}, new Object[]{"not_like", "name", "*ha*", 0L},
                new Object[]{"prefix", "name", "alpha", 5L}, new Object[]{"not_prefix", "name", "alpha", 0L},
                new Object[]{"suffix", "name", "5", 1L}, new Object[]{"not_suffix", "name", "5", 4L},
                new Object[]{"regex", "name", "alpha[12]", 2L}, new Object[]{"not_regex", "name", "alpha[12]", 3L},
                new Object[]{"exists", "optional", null, 4L}, new Object[]{"not_exists", "optional", null, 1L},
                new Object[]{"is_null", "optional", null, 1L}, new Object[]{"is_not_null", "optional", null, 4L});
        return Stream.of("primary", "secondary").flatMap(source -> cases.stream()
                .map(entry -> Arguments.of(source, entry[0], entry[1], entry[2], entry[3])));
    }

    @BeforeAll
    void seed() throws Exception {
        for (String source : List.of("primary", "secondary")) {
            String index = (source.equals("primary") ? "test-js7-" : "test-js8-") + UUID.randomUUID();
            fixture.request(source, "PUT", "/" + index,
                    "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},\"mappings\":{\"properties\":{\"run\":{\"type\":\"keyword\"},\"name\":{\"type\":\"keyword\"},\"status\":{\"type\":\"keyword\"},\"optional\":{\"type\":\"keyword\"},\"secret\":{\"type\":\"keyword\"},\"contact\":{\"type\":\"keyword\"},\"rank\":{\"type\":\"integer\"},\"amount\":{\"type\":\"double\"},\"occurredAt\":{\"type\":\"date\"},\"ip\":{\"type\":\"ip\"}}}}");
            ownedIndices.put(index, source);
            for (int i = 1; i <= 5; i++) {
                Map<String, Object> data = new LinkedHashMap<>(Map.of("run", run, "name", "alpha" + i,
                        "status", i % 2 == 0 ? "ready" : "done", "rank", i, "amount", i,
                        "contact", "contact-value", "secret", "secret-value", "occurredAt", "2024-01-0" + i + "T12:00:00Z", "ip", "10.0.0." + i));
                if (i < 5) data.put("optional", "present");
                fixture.request(source, "PUT", "/" + index + "/_doc/row" + i, codec.encode(data));
            }
            fixture.request(source, "POST", "/" + index + "/_refresh", null);
        }
    }

    @AfterAll
    void cleanup() throws Exception {
        for (Map.Entry<String, String> entry : ownedIndices.entrySet())
            fixture.delete(entry.getValue(), entry.getKey());
    }

    private String alias(String source) {
        return source.equals("primary") ? "sample7" : "sample8";
    }

    private QueryCondition condition(String field, String op, Object value) {
        QueryCondition.QueryConditionBuilder builder = QueryCondition.builder().field(field).op(op);
        if (value instanceof List<?>) builder.values(new ArrayList<>((List<?>) value));
        else builder.value(value);
        return builder.build();
    }

    private QueryCondition scoped(QueryCondition extra) {
        QueryCondition scope = condition("run", "eq", run);
        return extra == null ? scope : QueryCondition.builder().logic("and").conditions(List.of(scope, extra)).build();
    }

    private QueryRequest request(String source, QueryCondition extra) {
        return QueryRequest.builder().index(alias(source)).query(scoped(extra)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void mixedLogicDateAndIpCidrSelectExactRecords(String source) {
        QueryCondition either = QueryCondition.builder().logic("or").conditions(List.of(condition("_id", "eq", "row1"), condition("rank", "gte", 4))).build();
        assertEquals(3, engine.count(request(source, either)).getTotal());
        QueryRequest dates = request(source, null);
        dates.setDateRange(QueryRequest.DateRange.builder().from("2024-01-02").to("2024-01-03").build());
        assertEquals(2, engine.count(dates).getTotal());
        assertEquals(5, engine.count(request(source, condition("ip", "eq", "10.0.0.0/24"))).getTotal());
        assertEquals(0, engine.count(request(source, condition("ip", "eq", "10.1.0.0/24"))).getTotal());
        QueryResponse noMatch = engine.query(request(source, condition("rank", "gt", 10)));
        assertEquals(0, noMatch.getTotal());
        assertTrue(noMatch.getItems().isEmpty());
        assertFalse(noMatch.getPagination().getHasMore());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void remainingHttpEndpointsAndSafeFailures(String source) {
        AggRequest aggregate = AggRequest.builder().index(alias(source)).query(scoped(null)).aggs(List.of(AggDefinition.builder().name("amountSum").type("sum").field("amount").build())).build();
        ResponseEntity<Map> result = http.postForEntity("/api/agg", aggregate, Map.class);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(15.0, ((Number) ((Map<?, ?>) result.getBody().get("aggregations")).get("amountSum")).doubleValue());
        ResponseEntity<Map> directory = http.getForEntity("/api/indices", Map.class);
        assertEquals(HttpStatus.OK, directory.getStatusCode());
        assertEquals(2, ((List<?>) directory.getBody().get("indices")).size());
        assertEquals(HttpStatus.NO_CONTENT, http.exchange("/api/indices/mapping-cache", HttpMethod.PUT, HttpEntity.EMPTY, Void.class).getStatusCode());
        for (String kind : List.of("pit", "scroll")) {
            QueryRequest query = request(source, null);
            query.setPagination(PaginationInfo.builder().type(kind.equals("pit") ? "search_after" : "scroll").searchAfterMode("pit").size(2).build());
            QueryResponse first = engine.query(query);
            String token = kind.equals("pit") ? first.getPagination().getPitId() : first.getPagination().getScrollId();
            assertNotNull(token);
            assertEquals(HttpStatus.NO_CONTENT, http.exchange("/api/query/" + kind + "/" + token, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class).getStatusCode());
            ResponseEntity<Map> badToken = http.exchange("/api/query/" + kind + "/invalid-token", HttpMethod.DELETE, HttpEntity.EMPTY, Map.class);
            assertEquals(HttpStatus.BAD_REQUEST, badToken.getStatusCode());
            assertEquals(Set.of("message", "timestamp", "requestId"), badToken.getBody().keySet());
        }
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> malformed = http.postForEntity("/api/query", new HttpEntity<>("{", json), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
        assertEquals(Set.of("message", "timestamp", "requestId"), malformed.getBody().keySet());
        HttpHeaders plain = new HttpHeaders();
        plain.setContentType(MediaType.TEXT_PLAIN);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, http.postForEntity("/api/query", new HttpEntity<>("not-json", plain), Map.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.getForEntity("/api/indices/unknown/fields", Map.class).getStatusCode());
    }

    @ParameterizedTest
    @MethodSource("operators")
    void allQueryOperators(String source, String op, String field, Object value, long expected) {
        QueryResponse response = engine.query(request(source, condition(field, op, value)));
        assertEquals(expected, response.getTotal());
        assertEquals(expected, response.getItems().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void preciseCountAndIdQueries(String source) {
        QueryResponse counted = engine.count(request(source, null));
        assertEquals(5, counted.getTotal());
        assertNull(counted.getItems());
        assertNull(counted.getPagination());
        for (String op : List.of("eq", "ne", "in", "not_in")) {
            Object value = op.contains("in") ? List.of("row1") : "row1";
            long expected = op.equals("eq") || op.equals("in") ? 1 : 4;
            assertEquals(expected, engine.count(request(source, condition("_id", op, value))).getTotal());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void masksResultsAndRejectsLeakingFields(String source) {
        for (Map<String, Object> row : engine.query(request(source, null)).getItems()) {
            assertFalse(row.containsKey("secret"));
            assertNotEquals("contact-value", row.get("contact"));
        }
        assertThrows(RuntimeException.class, () -> engine.query(request(source, condition("secret", "eq", "secret-value"))));
        QueryRequest forbiddenProjection = request(source, null);
        forbiddenProjection.setFields(List.of("secret"));
        assertThrows(RuntimeException.class, () -> engine.query(forbiddenProjection));
        QueryRequest maskedSort = request(source, null);
        maskedSort.setPagination(PaginationInfo.builder().type("offset").sort(List.of(PaginationInfo.SortField.builder().field("contact").order("asc").build())).build());
        assertThrows(RuntimeException.class, () -> engine.query(maskedSort));
        assertThrows(RuntimeException.class, () -> engine.aggregate(AggRequest.builder().index(alias(source)).query(scoped(null))
                .aggs(List.of(AggDefinition.builder().name("leak").type("terms").field("contact").build())).build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void offsetProjectionCollapseAndBounds(String source) {
        QueryRequest request = request(source, null);
        request.setFields(List.of("amount"));
        request.setPagination(PaginationInfo.builder().type("offset").page(2).size(2)
                .sort(List.of(PaginationInfo.SortField.builder().field("rank").order("asc").build())).build());
        QueryResponse result = engine.query(request);
        assertEquals(5, result.getTotal());
        assertEquals(2, result.getItems().size());
        assertFalse(result.getItems().get(0).containsKey("status"));
        request.setFields(null);
        request.setPagination(PaginationInfo.builder().type("offset").page(1).size(20).build());
        request.setCollapse(QueryRequest.CollapseField.builder().field("status").build());
        assertEquals(2, engine.query(request).getItems().size());
        request.setPagination(PaginationInfo.builder().type("offset").page(Integer.MAX_VALUE).size(1000).build());
        assertThrows(RuntimeException.class, () -> engine.query(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void searchAfterAndPitReturnEveryDocumentOnce(String source) {
        for (String mode : List.of("tiebreaker", "none", "pit")) {
            QueryRequest request = request(source, null);
            PaginationInfo page = PaginationInfo.builder().type("search_after").size(2).searchAfterMode(mode).pitKeepAlive("1m")
                    .sort(List.of(PaginationInfo.SortField.builder().field("rank").order("asc").build())).build();
            request.setPagination(page);
            Set<Object> ids = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                QueryResponse result = engine.query(request);
                for (Map<String, Object> row : result.getItems()) assertTrue(ids.add(row.get("_id")), "续页不得重复");
                if (!Boolean.TRUE.equals(result.getPagination().getHasMore())) break;
                page.setSearchAfter(result.getPagination().getNextSearchAfter());
                page.setPitId(result.getPagination().getPitId());
            }
            assertEquals(5, ids.size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void scrollAndAuthenticatedCursor(String source) {
        QueryRequest request = request(source, null);
        PaginationInfo page = PaginationInfo.builder().type("scroll").size(2).scrollTtl("1m").build();
        request.setPagination(page);
        QueryResponse first = engine.query(request);
        String token = first.getPagination().getScrollId();
        assertNotNull(token);
        QueryRequest wrong = request(source.equals("primary") ? "secondary" : "primary", null);
        wrong.setPagination(PaginationInfo.builder().type("scroll").size(2).scrollId(token).scrollTtl("1m").build());
        assertThrows(RuntimeException.class, () -> engine.query(wrong));
        page.setScrollId((token.charAt(0) == 'A' ? "B" : "A") + token.substring(1));
        assertThrows(RuntimeException.class, () -> engine.query(request));
        page.setScrollId(token);
        Set<Object> ids = new HashSet<>();
        first.getItems().forEach(row -> ids.add(row.get("_id")));
        for (int i = 0; i < 5; i++) {
            QueryResponse next = engine.query(request);
            for (Map<String, Object> row : next.getItems()) assertTrue(ids.add(row.get("_id")));
            if (!Boolean.TRUE.equals(next.getPagination().getHasMore())) break;
            page.setScrollId(next.getPagination().getScrollId());
        }
        assertEquals(5, ids.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void explicitCursorReleaseAndChangedRequestRejection(String source) {
        QueryRequest request = request(source, null);
        request.setPagination(PaginationInfo.builder().type("search_after").searchAfterMode("pit").size(2).pitKeepAlive("1m")
                .sort(List.of(PaginationInfo.SortField.builder().field("rank").order("asc").build())).build());
        QueryResponse first = engine.query(request);
        String token = first.getPagination().getPitId();
        assertNotNull(token);
        request.getPagination().setPitId(token);
        request.getPagination().setSearchAfter(first.getPagination().getNextSearchAfter());
        request.setFields(List.of("amount"));
        assertThrows(RuntimeException.class, () -> engine.query(request));
        assertDoesNotThrow(() -> engine.closePit(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void metricsBucketsAndComposite(String source) {
        List<AggDefinition> definitions = new ArrayList<>();
        for (String type : List.of("sum", "avg", "min", "max", "count", "stats", "extended_stats", "percentiles", "percentile_ranks")) {
            AggDefinition.AggDefinitionBuilder definition = AggDefinition.builder()
                    .name(type.equals("count") ? "valueCount" : type).type(type).field("amount");
            if (type.equals("percentile_ranks")) definition.values(List.of(2.0, 4.0));
            definitions.add(definition.build());
        }
        definitions.add(AggDefinition.builder().name("cardinality").type("cardinality").field("status").build());
        definitions.add(AggDefinition.builder().name("terms").type("terms").field("status").build());
        definitions.add(AggDefinition.builder().name("dates").type("date_histogram").field("occurredAt").interval("day").build());
        definitions.add(AggDefinition.builder().name("histogram").type("histogram").field("amount").interval("2").build());
        definitions.add(AggDefinition.builder().name("range").type("range").field("amount")
                .ranges(List.of(AggDefinition.Range.builder().from(1).to(4).build())).build());
        definitions.add(AggDefinition.builder().name("dateRange").type("date_range").field("occurredAt")
                .ranges(List.of(AggDefinition.Range.builder().from("2024-01-01").to("2024-01-04").build())).build());
        definitions.add(AggDefinition.builder().name("ipRange").type("ip_range").field("ip")
                .ranges(List.of(AggDefinition.Range.builder().from("10.0.0.1").to("10.0.0.4").build())).build());
        definitions.add(AggDefinition.builder().name("filter").type("filter").query(condition("status", "eq", "ready")).build());
        definitions.add(AggDefinition.builder().name("filters").type("filters").filters(Map.of("ready", condition("status", "eq", "ready"))).build());
        definitions.add(AggDefinition.builder().name("missing").type("missing").field("optional").build());
        AggResponse response = engine.aggregate(AggRequest.builder().index(alias(source)).query(scoped(null)).aggs(definitions).build());
        assertEquals(19, response.getAggregations().size());
        assertNull(response.getRawResponse());
        assertEquals(15.0, ((Number) response.getAggregations().get("sum")).doubleValue());
        assertEquals(3.0, ((Number) response.getAggregations().get("avg")).doubleValue());
        assertEquals(1.0, ((Number) response.getAggregations().get("min")).doubleValue());
        assertEquals(5.0, ((Number) response.getAggregations().get("max")).doubleValue());
        assertEquals(5.0, ((Number) response.getAggregations().get("valueCount")).doubleValue());
        assertEquals(2.0, ((Number) response.getAggregations().get("cardinality")).doubleValue());
        for (String stats : List.of("stats", "extended_stats")) {
            Map<?, ?> values = (Map<?, ?>) response.getAggregations().get(stats);
            assertEquals(5, ((Number) values.get("count")).intValue());
            assertEquals(15.0, ((Number) values.get("sum")).doubleValue());
        }
        assertEquals(3.0, ((Number) ((Map<?, ?>) response.getAggregations().get("percentiles")).get("50.0")).doubleValue(), 0.01);
        Map<?, ?> ranks = (Map<?, ?>) response.getAggregations().get("percentile_ranks");
        assertTrue(((Number) ranks.get("2.0")).doubleValue() < ((Number) ranks.get("4.0")).doubleValue());
        assertEquals(5, ((List<?>) response.getAggregations().get("dates")).size());
        assertEquals(3, ((List<?>) response.getAggregations().get("histogram")).size());
        for (String bucket : List.of("range", "dateRange", "ipRange")) {
            assertEquals(3, ((Number) ((Map<?, ?>) ((List<?>) response.getAggregations().get(bucket)).get(0)).get("count")).intValue());
        }
        assertEquals(2, ((Number) ((Map<?, ?>) response.getAggregations().get("filter")).get("count")).intValue());
        assertEquals(1, ((Number) ((Map<?, ?>) response.getAggregations().get("missing")).get("count")).intValue());
        assertEquals(2, ((Number) ((Map<?, ?>) ((List<?>) response.getAggregations().get("filters")).get(0)).get("count")).intValue());
        AggRequest composite = AggRequest.builder().index(alias(source)).query(scoped(null))
                .aggs(List.of(AggDefinition.builder().name("groups").type("terms").field("status").composite(true).size(1).build())).build();
        AggResponse first = engine.aggregate(composite);
        assertNotNull(first.getAfterKey());
        composite.setAfter(first.getAfterKey());
        AggResponse second = engine.aggregate(composite);
        assertNotEquals(first.getAggregations().get("groups"), second.getAggregations().get("groups"));
        for (String reserved : List.of("key", "count")) {
            assertThrows(RuntimeException.class, () -> engine.aggregate(AggRequest.builder().index(alias(source)).query(scoped(null))
                    .aggs(List.of(AggDefinition.builder().name(reserved).type("count").field("amount").build())).build()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void nestedAndPipelineAggregations(String source) {
        AggDefinition nested = AggDefinition.builder().name("groups").type("terms").field("status")
                .aggs(List.of(AggDefinition.builder().name("amountSum").type("sum").field("amount").build()))
                .pipelineAggs(List.of(PipelineAggDefinition.builder().name("keepLarge").type("bucket_selector")
                        .script("params.value > 7").bucketsPath(Map.of("value", "amountSum")).build())).build();
        AggResponse response = engine.aggregate(AggRequest.builder().index(alias(source)).query(scoped(null)).aggs(List.of(nested)).build());
        List<?> buckets = (List<?>) response.getAggregations().get("groups");
        assertEquals(1, buckets.size());
        Map<?, ?> bucket = (Map<?, ?>) buckets.get(0);
        assertEquals("done", bucket.get("key"));
        assertEquals(9.0, ((Number) bucket.get("amountSum")).doubleValue());
        nested.setPipelineAggs(List.of(PipelineAggDefinition.builder().name("topBucket").type("bucket_sort")
                .sort(Map.of("amountSum", "desc")).size(1).build()));
        List<?> top = (List<?>) engine.aggregate(AggRequest.builder().index(alias(source)).query(scoped(null)).aggs(List.of(nested)).build())
                .getAggregations().get("groups");
        assertEquals(1, top.size());
        assertEquals("done", ((Map<?, ?>) top.get(0)).get("key"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void httpContractsAndMappingRefresh(String source) {
        ResponseEntity<Map> response = http.postForEntity("/api/query", request(source, null), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("total"));
        assertFalse(response.getBody().containsKey("code"));
        assertFalse(response.getBody().containsKey("data"));
        ResponseEntity<Map> fields = http.getForEntity("/api/indices/" + alias(source) + "/fields", Map.class);
        assertEquals(HttpStatus.OK, fields.getStatusCode());
        assertTrue(fields.getBody().containsKey("fields"));
        assertFalse(codec.encode(fields.getBody()).contains("secret"));
        assertTrue(codec.encode(fields.getBody()).contains("金额"));
        assertEquals(HttpStatus.NO_CONTENT, http.exchange("/api/indices/" + alias(source) + "/mapping-cache", HttpMethod.PUT, HttpEntity.EMPTY, Void.class).getStatusCode());
        QueryRequest bad = request(source, condition("unknownField", "eq", "x"));
        ResponseEntity<Map> rejected = http.postForEntity("/api/query", bad, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertEquals(Set.of("message", "timestamp", "requestId"), rejected.getBody().keySet());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, http.getForEntity("/api/query", Map.class).getStatusCode());
    }
}
