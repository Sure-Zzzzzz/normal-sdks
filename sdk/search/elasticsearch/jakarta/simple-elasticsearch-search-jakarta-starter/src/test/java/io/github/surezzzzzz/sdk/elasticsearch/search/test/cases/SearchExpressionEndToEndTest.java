package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionAggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionValidationRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionHintsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 官方 Condition 制品、真实 Search 宿主及两版 ES；数据和清理由本测试精确拥有。
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchExpressionEndToEndTest {
    private final Map<String, String> owned = new LinkedHashMap<>();
    @Autowired
    private ExpressionService expressions;
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
                new Object[]{"amount=3", 1L}, new Object[]{"amount!=3", 5L},
                new Object[]{"amount>3", 2L}, new Object[]{"amount>=3", 3L},
                new Object[]{"amount<3", 3L}, new Object[]{"amount<=3", 4L},
                new Object[]{"amount IN (1,3)", 3L}, new Object[]{"amount NOT IN (1,3)", 3L},
                new Object[]{"name LIKE 'alpha'", 6L}, new Object[]{"name NOT LIKE 'alpha'", 0L},
                new Object[]{"name PREFIX LIKE 'alpha'", 6L}, new Object[]{"name NOT PREFIX LIKE 'alpha'", 0L},
                new Object[]{"name SUFFIX LIKE '5'", 1L}, new Object[]{"name NOT SUFFIX LIKE '5'", 5L},
                new Object[]{"optional EXISTS", 5L}, new Object[]{"optional NOT EXISTS", 1L},
                new Object[]{"optional IS NULL", 1L}, new Object[]{"optional IS NOT NULL", 5L},
                new Object[]{"active=true", 3L}, new Object[]{"金额>3", 2L},
                new Object[]{"_id IN ('row1','row6')", 2L});
        return Stream.of("primary", "secondary").flatMap(source -> cases.stream()
                .map(item -> Arguments.of(source, item[0], item[1])));
    }

    @BeforeAll
    void seed() throws Exception {
        for (String source : List.of("primary", "secondary")) {
            String index = (source.equals("primary") ? "test-js7-" : "test-js8-") + UUID.randomUUID();
            fixture.request(source, "PUT", "/" + index,
                    "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},\"mappings\":{\"properties\":{"
                            + "\"rank\":{\"type\":\"integer\"},\"amount\":{\"type\":\"double\"},\"status\":{\"type\":\"keyword\"},"
                            + "\"name\":{\"type\":\"keyword\"},\"active\":{\"type\":\"boolean\"},\"optional\":{\"type\":\"keyword\"},"
                            + "\"occurredAt\":{\"type\":\"date\"},\"epoch\":{\"type\":\"long\"},\"secret\":{\"type\":\"keyword\"},"
                            + "\"contact\":{\"type\":\"keyword\"},\"labelValue\":{\"type\":\"keyword\"}}}}");
            owned.put(source, index);
            for (int row = 1; row <= 6; row++) {
                String date = "2024-01-0" + row + "T12:00:00Z";
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("rank", row);
                data.put("status", row % 2 == 0 ? "ready" : "done");
                data.put("name", "alpha" + row);
                data.put("active", row % 2 == 0);
                data.put("occurredAt", date);
                data.put("epoch", Instant.parse(date).getEpochSecond());
                data.put("contact", "mock-contact");
                data.put("secret", "mock-secret");
                data.put("labelValue", "金额");
                if (row <= 4) data.put("amount", row);
                if (row == 5) data.put("amount", List.of(1, 9));
                if (row != 6) data.put("optional", "present");
                fixture.request(source, "PUT", "/" + index + "/_doc/row" + row, codec.encode(data));
            }
            fixture.request(source, "POST", "/" + index + "/_refresh", null);
        }
    }

    @AfterAll
    void cleanup() throws Exception {
        for (Map.Entry<String, String> item : owned.entrySet()) fixture.delete(item.getKey(), item.getValue());
    }

    @ParameterizedTest
    @MethodSource("operators")
    void allExpressionOperatorsRunAgainstBothEsVersions(String source, String expression, long expected) {
        QueryResponse query = expressions.query(request(source, expression));
        assertEquals(expected, query.getTotal());
        assertEquals(expected, query.getItems().size());
        ExpressionQueryRequest counted = request(source, expression);
        counted.setCountOnly(true);
        QueryResponse count = expressions.query(counted);
        assertEquals(expected, count.getTotal());
        assertNull(count.getItems());
        assertNull(count.getPagination());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void numericAndBooleanLookingKeywordsAndIdsKeepTheirExactSpelling(String source) throws Exception {
        String index = (source.equals("primary") ? "test-js7-" : "test-js8-") + UUID.randomUUID();
        try {
            fixture.request(source, "PUT", "/" + index,
                    "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
                            + "\"mappings\":{\"properties\":{\"code\":{\"type\":\"keyword\"}}}}");
            for (String code : List.of("001", "1", "TRUE", "true"))
                fixture.request(source, "PUT", "/" + index + "/_doc/" + code, codec.encode(Map.of("code", code)));
            fixture.request(source, "POST", "/" + index + "/_refresh", null);
            for (String expression : List.of("code='001'", "code=001", "_id='001'", "_id=001"))
                assertEquals(Set.of("001"), ids(expressions.query(ExpressionQueryRequest.builder().index(index).expression(expression).build())));
            for (String expression : List.of("code=TRUE", "_id='TRUE'"))
                assertEquals(Set.of("TRUE"), ids(expressions.query(ExpressionQueryRequest.builder().index(index).expression(expression).build())));
            for (String expression : List.of("code IN ('001','TRUE')", "_id IN ('001','TRUE')"))
                assertEquals(Set.of("001", "TRUE"), ids(expressions.query(ExpressionQueryRequest.builder().index(index).expression(expression).build())));
        } finally {
            fixture.delete(source, index);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void negationPreservesMissingAndMultiValueFieldSemantics(String source) {
        assertEquals(Set.of("row1", "row2", "row3", "row6"), ids(expressions.query(request(source, "NOT amount>3"))));
        assertEquals(Set.of("row4", "row5"), ids(expressions.query(request(source, "NOT NOT amount>3"))));
        assertEquals(Set.of("row1", "row3"), ids(expressions.query(request(source, "NOT (amount>3 OR status='ready')"))));
        assertEquals(Set.of("row1", "row2", "row3", "row6"), ids(expressions.query(request(source, "非 (金额 大于 3)"))));
        assertEquals(6, expressions.query(request(source, "labelValue='金额'")).getTotal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void timeKeywordsQueryBothDateAndEpochFields(String source) {
        for (String field : List.of("occurredAt", "epoch")) {
            ExpressionQueryRequest request = request(source, field + "=昨天");
            request.setTimeRangeAnchor(Instant.parse("2024-01-04T12:00:00Z"));
            assertEquals(Set.of("row3"), ids(expressions.query(request)));
            request.setTimeZone("America/New_York");
            assertEquals(Set.of("row3"), ids(expressions.query(request)));
        }
        ExpressionQueryRequest dateRange = request(source, "rank>0");
        dateRange.setDateRange(QueryRequest.DateRange.builder().from("2024-01-02").to("2024-01-03").build());
        assertEquals(Set.of("row2", "row3"), ids(expressions.query(dateRange)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void expressionsKeepProjectionMaskAndSensitiveGates(String source) {
        ExpressionQueryRequest request = request(source, "rank>0");
        request.setFields(List.of("amount", "contact"));
        request.setPagination(PaginationInfo.builder().type("offset").size(2).page(2)
                .sort(List.of(PaginationInfo.SortField.builder().field("rank").order("asc").build())).build());
        QueryResponse response = expressions.query(request);
        assertEquals(6, response.getTotal());
        assertEquals(2, response.getItems().size());
        for (Map<String, Object> item : response.getItems()) {
            assertEquals("****", item.get("contact"));
            assertFalse(item.containsKey("status"));
            assertFalse(item.containsKey("secret"));
        }
        assertThrows(SimpleElasticsearchSearchException.class, () -> expressions.query(request(source, "secret='mock-secret'")));
        assertThrows(SimpleElasticsearchSearchException.class, () -> expressions.query(request(source, "unknown=1")));
        ExpressionQueryRequest bad = request(source, "rank>0");
        bad.setFields(List.of("secret"));
        assertThrows(SimpleElasticsearchSearchException.class, () -> expressions.query(bad));
        bad.setFields(null);
        bad.setPagination(PaginationInfo.builder().sort(List.of(PaginationInfo.SortField.builder().field("contact").order("asc").build())).build());
        assertThrows(SimpleElasticsearchSearchException.class, () -> expressions.query(bad));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void anchoredExpressionsSupportAllCursorModesWithoutDuplicates(String source) {
        for (String mode : List.of("tiebreaker", "none", "pit", "scroll")) {
            ExpressionQueryRequest request = request(source, "occurredAt=近7天");
            request.setTimeRangeAnchor(Instant.parse("2024-01-07T12:00:00Z"));
            PaginationInfo page = PaginationInfo.builder().type(mode.equals("scroll") ? "scroll" : "search_after")
                    .searchAfterMode(mode.equals("scroll") ? "pit" : mode).size(2)
                    .sort(List.of(PaginationInfo.SortField.builder().field("rank").order("asc").build())).build();
            request.setPagination(page);
            Set<String> seen = new HashSet<>();
            String latest = null;
            try {
                for (int count = 0; count < 5; count++) {
                    QueryResponse response = expressions.query(request);
                    for (String id : ids(response)) assertTrue(seen.add(id), "表达式续页不得重复");
                    latest = mode.equals("scroll") ? response.getPagination().getScrollId() : response.getPagination().getPitId();
                    if (!Boolean.TRUE.equals(response.getPagination().getHasMore())) break;
                    page.setPitId(response.getPagination().getPitId());
                    page.setScrollId(response.getPagination().getScrollId());
                    page.setSearchAfter(response.getPagination().getNextSearchAfter());
                }
                assertEquals(Set.of("row1", "row2", "row3", "row4", "row5", "row6"), seen);
            } finally {
                if (latest != null) {
                    if (mode.equals("scroll")) engine.clearScroll(latest);
                    else engine.closePit(latest);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void missingAnchorAndChangedTimeContextCannotResume(String source) {
        ExpressionQueryRequest request = request(source, "occurredAt=近7天");
        request.setPagination(PaginationInfo.builder().type("search_after").searchAfterMode("pit").size(2).build());
        assertThrows(SimpleElasticsearchSearchException.class, () -> expressions.query(request));
        request.setTimeRangeAnchor(Instant.parse("2024-01-07T12:00:00Z"));
        QueryResponse first = expressions.query(request);
        String token = first.getPagination().getPitId();
        try {
            request.getPagination().setPitId(token);
            request.getPagination().setSearchAfter(first.getPagination().getNextSearchAfter());
            request.setTimeRangeAnchor(Instant.parse("2024-01-08T12:00:00Z"));
            assertThrows(SimpleElasticsearchSearchException.class, () -> expressions.query(request));
            request.setTimeRangeAnchor(Instant.parse("2024-01-07T12:00:00Z"));
            request.setIndex(owned.get(source.equals("primary") ? "secondary" : "primary"));
            assertThrows(SimpleElasticsearchSearchException.class, () -> expressions.query(request));
        } finally {
            engine.closePit(token);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void filteredAggregationAndCompositeReuseSameTimeAnchor(String source) {
        ExpressionAggRequest request = ExpressionAggRequest.builder().index(owned.get(source)).expression("status='ready'")
                .aggs(List.of(AggDefinition.builder().name("amountSum").type("sum").field("amount").build())).build();
        assertEquals(6.0, ((Number) expressions.aggregate(request).getAggregations().get("amountSum")).doubleValue());
        request.setExpression("occurredAt=近7天");
        request.setAggs(List.of(AggDefinition.builder().name("groups").type("terms").field("status").composite(true).size(1).build()));
        assertThrows(SimpleElasticsearchSearchException.class, () -> expressions.aggregate(request));
        request.setTimeRangeAnchor(Instant.parse("2024-01-07T12:00:00Z"));
        AggResponse first = expressions.aggregate(request);
        request.setAfter(first.getAfterKey());
        AggResponse second = expressions.aggregate(request);
        assertNotEquals(first.getAggregations().get("groups"), second.getAggregations().get("groups"));
        assertNull(second.getRawResponse());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void httpExpressionContractsAndErrorsAreSafe(String source) {
        ResponseEntity<Map> query = http.postForEntity("/api/query/expression", request(source, "amount=3"), Map.class);
        assertEquals(HttpStatus.OK, query.getStatusCode());
        assertEquals(1, query.getBody().get("total"));
        assertFalse(query.getBody().containsKey("data"));
        assertFalse(query.getBody().containsKey("code"));
        ExpressionAggRequest agg = ExpressionAggRequest.builder().index(owned.get(source)).expression("status='ready'")
                .aggs(List.of(AggDefinition.builder().name("amountSum").type("sum").field("amount").build())).build();
        ResponseEntity<Map> aggregated = http.postForEntity("/api/agg/expression", agg, Map.class);
        assertEquals(HttpStatus.OK, aggregated.getStatusCode());
        assertEquals(6.0, ((Number) ((Map<?, ?>) aggregated.getBody().get("aggregations")).get("amountSum")).doubleValue());
        ExpressionValidationRequest validation = ExpressionValidationRequest.builder().index(owned.get(source)).expression("amount=3").build();
        ResponseEntity<Map> valid = http.postForEntity("/api/expression/validation", validation, Map.class);
        assertEquals(HttpStatus.OK, valid.getStatusCode());
        assertEquals(true, valid.getBody().get("valid"));
        validation.setExpression("unknown='private-sentinel'");
        ResponseEntity<Map> invalid = http.postForEntity("/api/expression/validation", validation, Map.class);
        assertEquals(HttpStatus.OK, invalid.getStatusCode());
        assertEquals(false, invalid.getBody().get("valid"));
        assertFalse(codec.encode(invalid.getBody()).contains("private-sentinel"));
        ExpressionHintsResponse hints = http.getForObject("/api/expression/hints?index=" + owned.get(source), ExpressionHintsResponse.class);
        assertNotNull(hints);
        assertTrue(hints.getOperators().contains("NOT"));
        assertTrue(hints.getTimeRanges().contains("昨天"));
        assertFalse(hints.getFields().stream().anyMatch(field -> field.getName().equals("secret")));
        assertTrue(hints.getFields().stream().anyMatch(field -> field.getName().equals("amount") && field.getLabels().contains("金额")));
        ResponseEntity<Map> rejected = http.postForEntity("/api/query/expression", request(source, "secret='private-sentinel'"), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertEquals(Set.of("message", "timestamp", "requestId"), rejected.getBody().keySet());
        assertFalse(codec.encode(rejected.getBody()).contains("private-sentinel"));
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/api/query/expression", new HttpEntity<>("{", json), Map.class).getStatusCode());
        HttpHeaders plain = new HttpHeaders();
        plain.setContentType(MediaType.TEXT_PLAIN);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, http.postForEntity("/api/query/expression", new HttpEntity<>("input", plain), Map.class).getStatusCode());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, http.getForEntity("/api/query/expression", Map.class).getStatusCode());
    }

    private ExpressionQueryRequest request(String source, String expression) {
        return ExpressionQueryRequest.builder().index(owned.get(source)).expression(expression).build();
    }

    private Set<String> ids(QueryResponse response) {
        Set<String> result = new HashSet<>();
        for (Map<String, Object> item : response.getItems())
            assertTrue(result.add((String) item.get("_id")), "页内不能重复");
        return result;
    }
}
