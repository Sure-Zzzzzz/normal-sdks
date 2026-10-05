package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.AggregationResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.DefaultAggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.DefaultSearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.SearchIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.AesGcmCursorTokenCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.JacksonSearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchRestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 在真实引擎中注入受控外部响应，核对清理顺序、错误事件和分页拒绝，不称为网络故障验收。
 */
@Slf4j
class SearchLifecycleTest {
    private final List<Object> events = new ArrayList<>();
    private SimpleElasticsearchSearchProperties properties;
    private SearchRestHelper rest;
    private SearchIndexResolver indices;
    private MappingManager mappings;
    private DefaultSearchEngine engine;
    private ResolvedIndex target;

    @BeforeEach
    void setup() {
        properties = new SimpleElasticsearchSearchProperties();
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        properties.getCursor().setEncryptionKey(Base64.getEncoder().encodeToString(key));
        SimpleElasticsearchSearchProperties.IndexConfig config = new SimpleElasticsearchSearchProperties.IndexConfig();
        config.setName("sample-record");
        config.setAlias("sample");
        config.setTiebreakerField("rank");
        target = new ResolvedIndex(config, "sample", new String[]{"sample-record"}, "primary", null, null, 0);
        rest = mock(SearchRestHelper.class);
        indices = mock(SearchIndexResolver.class);
        mappings = mock(MappingManager.class);
        when(indices.resolve(eq("sample"), any())).thenReturn(target);
        when(indices.resume(eq("sample"), any(), anyString(), any(), any(), anyInt())).thenReturn(target);
        when(mappings.fields(target)).thenReturn(Map.of("rank", new FieldMetadata("rank", "integer", null, true, true, false, false, Collections.emptyList())));
        configure(events::add);
    }

    private void configure(ApplicationEventPublisher publisher) {
        SearchPayloadCodec codec = new JacksonSearchPayloadCodec();
        SensitiveFieldProcessor sensitive = new SensitiveFieldProcessor();
        SearchDslBuilder queries = new DefaultSearchDslBuilder(properties, sensitive);
        engine = new DefaultSearchEngine(properties, codec, rest, indices, mappings, sensitive, queries,
                new DefaultAggregationDslBuilder(queries, sensitive, properties), new DefaultSearchResponseParser(sensitive, properties),
                new AggregationResponseParser(), new AesGcmCursorTokenCodec(properties, codec), publisher);
    }

    private QueryRequest request(String kind) {
        return QueryRequest.builder().index("sample").pagination(PaginationInfo.builder().type(kind.equals("pit") ? "search_after" : kind)
                .searchAfterMode("pit").size(1).build()).build();
    }

    private Map<String, Object> response(String kind, long total, boolean invalid) {
        Map<String, Object> result = new LinkedHashMap<>(Map.of("took", 1, "timed_out", false, "_shards", Map.of("failed", 0),
                "hits", Map.of("total", Map.of("value", total, "relation", invalid ? "gte" : "eq"), "hits", Collections.singletonList(
                        Map.of("_id", "row1", "_index", "sample-record", "_source", Map.of("rank", 1), "sort", Collections.singletonList(1))))));
        if (kind.equals("pit")) result.put("pit_id", "latest-resource");
        if (kind.equals("scroll")) result.put("_scroll_id", "latest-resource");
        return result;
    }

    private void stub(String kind, Map<String, Object> response) {
        if (kind.equals("pit"))
            when(rest.perform(eq("primary"), eq("POST"), eq("/sample-record/_pit"), anyMap(), isNull())).thenReturn(Map.of("id", "opened-resource"));
        when(rest.perform(eq("primary"), eq("POST"), eq(kind.equals("pit") ? "/_search" : "/sample-record/_search"), anyMap(), anyMap())).thenReturn(response);
        when(rest.perform(eq("primary"), eq("DELETE"), anyString(), anyMap(), anyMap())).thenReturn(Map.of("succeeded", true));
    }

    private void verifyReleased(String kind) {
        verify(rest).perform("primary", "DELETE", kind.equals("pit") ? "/_pit" : "/_search/scroll", Collections.emptyMap(),
                kind.equals("pit") ? Map.of("id", "latest-resource") : Map.of("scroll_id", Collections.singletonList("latest-resource")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pit", "scroll"})
    void finalPageReleasesLatestResourceNotOriginalId(String kind) {
        stub(kind, response(kind, 1, false));
        QueryResponse result = engine.query(request(kind));
        assertEquals(1, result.getTotal());
        assertFalse(result.getPagination().getHasMore());
        verifyReleased(kind);
        assertNull(result.getPagination().getPitId());
        assertNull(result.getPagination().getScrollId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pit", "scroll"})
    void parseFailureReleasesLatestResourceAndPublishesSafeError(String kind) {
        stub(kind, response(kind, 1, true));
        SimpleElasticsearchSearchException error = assertThrows(SimpleElasticsearchSearchException.class, () -> engine.query(request(kind)));
        verifyReleased(kind);
        EsQueryErrorEvent event = assertInstanceOf(EsQueryErrorEvent.class, events.get(0));
        assertNull(event.getError().getCause());
        assertNotSame(error, event.getError());
        assertNull(event.getRequest().getQuery());
        assertNull(event.getRequest().getIndex());
    }

    @Test
    void pitHttpFailureClosesOpenedResource() {
        stub("pit", response("pit", 1, false));
        when(rest.perform(eq("primary"), eq("POST"), eq("/_search"), anyMap(), anyMap())).thenThrow(new SimpleElasticsearchSearchException("controlled", "受控外部失败", 503));
        assertEquals(503, assertThrows(SimpleElasticsearchSearchException.class, () -> engine.query(request("pit"))).getStatus());
        verify(rest).perform("primary", "DELETE", "/_pit", Collections.emptyMap(), Map.of("id", "opened-resource"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pit", "scroll"})
    void automaticCleanupFailureDoesNotReplaceSuccessfulResult(String kind) {
        stub(kind, response(kind, 1, false));
        when(rest.perform(eq("primary"), eq("DELETE"), anyString(), anyMap(), anyMap())).thenThrow(new IllegalStateException("受控清理失败"));
        assertEquals(1, engine.query(request(kind)).getTotal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pit", "scroll"})
    void explicitCloseFailureIsVisible(String kind) {
        stub(kind, response(kind, 2, false));
        QueryResponse first = engine.query(request(kind));
        String token = kind.equals("pit") ? first.getPagination().getPitId() : first.getPagination().getScrollId();
        assertNotNull(token);
        when(rest.perform(eq("primary"), eq("DELETE"), anyString(), anyMap(), anyMap())).thenReturn(Map.of("succeeded", false));
        assertThrows(SimpleElasticsearchSearchException.class, () -> {
            if (kind.equals("pit")) engine.closePit(token);
            else engine.clearScroll(token);
        });
        verifyReleased(kind);
    }

    @Test
    void countOnlyIgnoresPaginationWithoutOpeningCursorOrReturningHits() {
        when(rest.perform(eq("primary"), eq("POST"), eq("/sample-record/_count"), anyMap(), anyMap())).thenReturn(Map.of("count", 0, "_shards", Map.of("failed", 0)));
        QueryRequest request = request("pit");
        request.getPagination().setSize(-1);
        request.setCountOnly(true);
        QueryResponse result = engine.query(request);
        assertEquals(0, result.getTotal());
        assertNull(result.getItems());
        assertNull(result.getPagination());
        verify(rest, times(1)).perform(anyString(), anyString(), anyString(), anyMap(), any());
    }

    @Test
    void paginationAndCollapseValidationHappensBeforeSearch() {
        for (PaginationInfo page : Arrays.asList(PaginationInfo.builder().size(0).build(), PaginationInfo.builder().size(Integer.MAX_VALUE).build(),
                PaginationInfo.builder().page(0).build(), PaginationInfo.builder().type("offset").page(Integer.MAX_VALUE).size(1000).build(),
                PaginationInfo.builder().type("unknown").build(), PaginationInfo.builder().type("scroll").scrollTtl("invalid").build(),
                PaginationInfo.builder().type("search_after").searchAfterMode("none").build())) {
            assertThrows(SimpleElasticsearchSearchException.class, () -> engine.query(QueryRequest.builder().index("sample").pagination(page).build()));
        }
        QueryRequest collapse = request("scroll");
        collapse.setCollapse(QueryRequest.CollapseField.builder().field("rank").build());
        assertThrows(SimpleElasticsearchSearchException.class, () -> engine.query(collapse));
        verifyNoInteractions(rest);
    }

    @Test
    void successEventsContainOnlyIndependentStatisticsAndListenersCannotChangeResult() {
        stub("offset", response("offset", 1, false));
        QueryResponse result = engine.query(request("offset"));
        EsQueryEvent event = assertInstanceOf(EsQueryEvent.class, events.get(0));
        assertNull(event.getRequest().getIndex());
        assertNull(event.getRequest().getQuery());
        assertNull(event.getResponse().getItems());
        assertNull(event.getResponse().getPagination());
        assertNotSame(result, event.getResponse());
        result.setTotal(99L);
        assertEquals(1, event.getResponse().getTotal());
        configure(value -> {
            throw new IllegalStateException("受控监听器失败");
        });
        assertEquals(1, engine.query(request("offset")).getTotal());
    }

    @Test
    void aggregationEventsAreSafeAndDoNotShareMutableResult() {
        when(rest.perform(eq("primary"), eq("POST"), eq("/sample-record/_search"), anyMap(), anyMap())).thenReturn(Map.of("took", 1, "_shards", Map.of("failed", 0), "aggregations", Map.of("amountSum", Map.of("value", 3))));
        AggRequest request = AggRequest.builder().index("sample").aggs(Collections.singletonList(AggDefinition.builder().name("amountSum").type("sum").field("rank").build())).build();
        assertEquals(3.0, ((Number) engine.aggregate(request).getAggregations().get("amountSum")).doubleValue());
        EsAggEvent success = assertInstanceOf(EsAggEvent.class, events.get(0));
        assertNull(success.getRequest().getIndex());
        assertTrue(success.getRequest().getAggs().isEmpty());
        assertNull(success.getResponse().getAggregations());
        when(rest.perform(eq("primary"), eq("POST"), anyString(), anyMap(), anyMap())).thenThrow(new IllegalStateException("private-detail"));
        assertThrows(SimpleElasticsearchSearchException.class, () -> engine.aggregate(request));
        EsAggErrorEvent failure = assertInstanceOf(EsAggErrorEvent.class, events.get(1));
        assertNull(failure.getError().getCause());
        assertFalse(failure.getError().getMessage().contains("private-detail"));
    }
}
