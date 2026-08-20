package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.catalog.ElasticsearchIndexListResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field.ElasticsearchFieldCapabilitiesRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field.ElasticsearchFieldCapabilitiesResponse;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Elasticsearch 索引目录安全投影测试。
 *
 * @author surezzzzzz
 */
class DefaultElasticsearchOperationsViewAdapterTest {

    private final DefaultElasticsearchOperationsViewAdapter adapter =
            new DefaultElasticsearchOperationsViewAdapter(mock(SimpleElasticsearchRouteRegistry.class), 1000L);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnOpenNonHiddenIndicesInStableOrder() {
        ElasticsearchIndexListResponse response = adapter.parseIndexList("search-primary", content("{\n"
                + "  \"z-order\": {\"aliases\": {}},\n"
                + "  \".tasks\": {\"aliases\": {}},\n"
                + "  \"a-order\": {\"aliases\": {}},\n"
                + "  \"m-order\": {\"aliases\": {}}\n"
                + "}"));

        assertEquals("search-primary", response.getDatasourceKey());
        assertEquals(asList("a-order", "m-order", "z-order"), response.getItems());
        assertFalse(response.isTruncated());
    }

    @Test
    void shouldLimitIndexDirectoryToFirstHundredNames() {
        StringBuilder body = new StringBuilder("{");
        for (int index = 0; index <= SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_INDEX_LIST_SIZE; index++) {
            if (index > 0) {
                body.append(',');
            }
            body.append('\"').append(String.format("index-%03d", index)).append("\":{}");
        }
        body.append('}');

        ElasticsearchIndexListResponse response = adapter.parseIndexList("search-primary", content(body.toString()));

        assertTrue(response.isTruncated());
        assertEquals(SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_INDEX_LIST_SIZE, response.getItems().size());
        assertEquals("index-000", response.getItems().get(0));
        assertEquals("index-099", response.getItems().get(99));
    }

    @Test
    void shouldMapMalformedDirectoryResponseToSafeError() {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> adapter.parseIndexList("search-primary", content("[]")));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Elasticsearch 运维查询暂不可用", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldProjectSafeFieldCapabilitiesInStableOrder() {
        ElasticsearchFieldCapabilitiesResponse response = adapter.parseFieldCapabilities("search-primary", "orders",
                content("{\"fields\":{\"zeta\":{\"keyword\":{\"searchable\":true,\"aggregatable\":true}},"
                        + "\"_id\":{\"_id\":{\"searchable\":true,\"aggregatable\":false}},"
                        + "\"alpha\":{\"text\":{\"searchable\":true,\"aggregatable\":false},"
                        + "\"keyword\":{\"searchable\":false,\"aggregatable\":true}},"
                        + "\"disabled\":{\"keyword\":{\"searchable\":false,\"aggregatable\":false}}}}"));

        assertEquals("search-primary", response.getDatasourceKey());
        assertEquals("orders", response.getIndex());
        assertFalse(response.isTruncated());
        assertEquals(2, response.getItems().size());
        assertEquals("alpha", response.getItems().get(0).getName());
        assertEquals(asList("keyword", "text"), response.getItems().get(0).getTypes());
        assertTrue(response.getItems().get(0).isSearchable());
        assertTrue(response.getItems().get(0).isAggregatable());
        assertEquals("zeta", response.getItems().get(1).getName());
    }

    @Test
    void shouldLimitFieldCapabilitiesToFirstTwoHundredNames() {
        StringBuilder body = new StringBuilder("{\"fields\":{");
        for (int index = 0; index <= SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_FIELD_CAPABILITIES_SIZE; index++) {
            if (index > 0) {
                body.append(',');
            }
            body.append('\"').append(String.format("field-%03d", index))
                    .append("\":{\"keyword\":{\"searchable\":true,\"aggregatable\":true}}");
        }
        body.append("}}");

        ElasticsearchFieldCapabilitiesResponse response = adapter.parseFieldCapabilities("search-primary", "orders",
                content(body.toString()));

        assertTrue(response.isTruncated());
        assertEquals(SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_FIELD_CAPABILITIES_SIZE,
                response.getItems().size());
        assertEquals("field-000", response.getItems().get(0).getName());
        assertEquals("field-199", response.getItems().get(199).getName());
    }

    @Test
    void shouldMapMalformedFieldCapabilitiesToSafeError() {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> adapter.parseFieldCapabilities("search-primary", "orders", content("[]")));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Elasticsearch 字段补全暂不可用", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldRequestOnlyExactIndexFieldCapabilities() throws Exception {
        RequestCapture capture = requestCapture("{\"fields\":{\"state\":{\"keyword\":{\"searchable\":true,"
                + "\"aggregatable\":true}}}}");

        ElasticsearchFieldCapabilitiesResponse value = capture.adapter.getFieldCapabilities(
                ElasticsearchFieldCapabilitiesRequest.builder().datasourceKey("search-primary").index("orders-2026.08")
                        .build());

        assertEquals("GET", capture.request.get().getMethod());
        assertEquals("/orders-2026.08/_field_caps", capture.request.get().getEndpoint());
        assertEquals("*", capture.request.get().getParameters().get("fields"));
        assertEquals(1, capture.request.get().getParameters().size());
        assertEquals(asList("state"), names(value));
    }

    @Test
    void shouldCancelFieldCapabilitiesRequestWhenDeadlineExpires() {
        SimpleElasticsearchRouteRegistry registry = mock(SimpleElasticsearchRouteRegistry.class);
        RestClient client = mock(RestClient.class);
        Cancellable cancellable = mock(Cancellable.class);
        when(registry.getClusterInfo("search-primary")).thenReturn(mock(ClusterInfo.class));
        when(registry.getLowLevelClient("search-primary")).thenReturn(client);
        when(client.performRequestAsync(any(Request.class), any(ResponseListener.class))).thenReturn(cancellable);
        DefaultElasticsearchOperationsViewAdapter timedOut = new DefaultElasticsearchOperationsViewAdapter(registry, 0L);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> timedOut.getFieldCapabilities(ElasticsearchFieldCapabilitiesRequest.builder()
                        .datasourceKey("search-primary").index("orders").build()));

        assertEquals(504, exception.getStatus().value());
        assertEquals("Elasticsearch 运维查询已超时", exception.getMessage());
        assertNotNull(exception.getCause());
        verify(cancellable).cancel();
    }

    @Test
    void shouldKeepDocumentQueryTimeoutSafeAndCancellable() {
        SimpleElasticsearchRouteRegistry registry = mock(SimpleElasticsearchRouteRegistry.class);
        RestClient client = mock(RestClient.class);
        Cancellable cancellable = mock(Cancellable.class);
        when(registry.getClusterInfo("search-primary")).thenReturn(mock(ClusterInfo.class));
        when(registry.getLowLevelClient("search-primary")).thenReturn(client);
        when(client.performRequestAsync(any(Request.class), any(ResponseListener.class))).thenReturn(cancellable);
        DefaultElasticsearchOperationsViewAdapter timedOut = new DefaultElasticsearchOperationsViewAdapter(registry, 0L);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> timedOut.queryDocuments(documentRequest("{\"size\":0}")));

        assertEquals(504, exception.getStatus().value());
        assertEquals("Elasticsearch 运维查询已超时", exception.getMessage());
        assertNotNull(exception.getCause());
        verify(cancellable).cancel();
    }

    @Test
    void shouldRejectOversizedFieldCapabilitiesResponse() {
        RequestCapture capture = requestCapture("{\"fields\":{},\"padding\":\""
                + repeat('x', SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_FIELD_CAPABILITIES_RESPONSE_LENGTH) + "\"}");

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> capture.adapter.getFieldCapabilities(ElasticsearchFieldCapabilitiesRequest.builder()
                        .datasourceKey("search-primary").index("orders").build()));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Elasticsearch 字段补全暂不可用", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldSendOriginalDslAndReturnOriginalElasticsearchStructure() throws Exception {
        String dsl = "{\"query\":{\"match_all\":{}},\"from\":0,\"size\":1}";
        RequestCapture capture = requestCapture("{\"took\":3,\"timed_out\":false,\"hits\":{\"total\":{\"value\":21,\"relation\":\"eq\"},"
                + "\"hits\":[{\"_index\":\"orders\",\"_id\":\"1\",\"_source\":{\"state\":\"OPEN\"}}]}}");

        ElasticsearchDocumentQueryResponse response = capture.adapter.queryDocuments(documentRequest(dsl));

        Request request = capture.request.get();
        assertEquals("POST", request.getMethod());
        assertEquals("/orders/_search", request.getEndpoint());
        assertEquals("open", request.getParameters().get("expand_wildcards"));
        assertEquals(objectMapper.readTree(dsl), objectMapper.readTree(entityText(request.getEntity())));
        assertEquals(3, response.getValue().path("took").asInt());
        assertEquals(21, response.getValue().path("hits").path("total").path("value").asInt());
        assertEquals("OPEN", response.getValue().path("hits").path("hits").get(0).path("_source").path("state").asText());
    }

    @Test
    void shouldSendWildcardIndexPatternsToSearchAsOneTarget() {
        RequestCapture capture = requestCapture("{\"took\":1,\"hits\":{\"hits\":[]}}");

        capture.adapter.queryDocuments(documentRequest("orders-*,events-*", "{\"size\":0}"));

        Request request = capture.request.get();
        assertEquals("/orders-*,events-*/_search", request.getEndpoint());
        assertEquals("open", request.getParameters().get("expand_wildcards"));
    }

    @Test
    void shouldReturnOriginalAggregationResponse() throws Exception {
        RequestCapture capture = requestCapture("{\"took\":1,\"hits\":{\"total\":{\"value\":21,\"relation\":\"eq\"},\"hits\":[]},"
                + "\"aggregations\":{\"sequence_stats\":{\"count\":21,\"min\":1.0,\"max\":21.0,\"avg\":11.0,\"sum\":231.0}}}");

        ElasticsearchDocumentQueryResponse response = capture.adapter.queryDocuments(documentRequest("{\"size\":0,\"aggs\":{"
                + "\"sequence_stats\":{\"stats\":{\"field\":\"sequence\"}}}}"));

        assertEquals(0, objectMapper.readTree(entityText(capture.request.get().getEntity())).path("size").asInt());
        assertEquals(21, response.getValue().path("aggregations").path("sequence_stats").path("count").asInt());
        assertEquals(231.0, response.getValue().path("aggregations").path("sequence_stats").path("sum").asDouble());
    }

    @Test
    void shouldAcceptDocumentResponseLargerThanCommonValueLimit() {
        RequestCapture capture = requestCapture("{\"hits\":{\"hits\":[]},\"padding\":\"" + repeat('x', 4097) + "\"}");

        ElasticsearchDocumentQueryResponse response = capture.adapter.queryDocuments(
                documentRequest("{\"query\":{\"match_all\":{}}}"));

        assertTrue(response.getValue().has("hits"));
    }

    @Test
    void shouldRejectDocumentResponseExceedingDedicatedLimitWithCause() {
        RequestCapture capture = requestCapture("{\"hits\":{\"hits\":[]},\"padding\":\"" + repeat('x',
                SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_DOCUMENT_RESPONSE_LENGTH) + "\"}");

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> capture.adapter.queryDocuments(documentRequest("{\"query\":{\"match_all\":{}}}")));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Elasticsearch 运维查询暂不可用", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldMapMalformedRawSearchResponseToSafeError() {
        RequestCapture capture = requestCapture("[]");

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> capture.adapter.queryDocuments(documentRequest("{\"query\":{\"match_all\":{}}}")));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Elasticsearch 运维查询暂不可用", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldRetainDownstreamFailureCauseAndSafeStatus() {
        SimpleElasticsearchRouteRegistry registry = mock(SimpleElasticsearchRouteRegistry.class);
        RestClient client = mock(RestClient.class);
        IOException downstreamFailure = new IOException("下游连接失败");
        when(registry.getClusterInfo("search-primary")).thenReturn(mock(ClusterInfo.class));
        when(registry.getLowLevelClient("search-primary")).thenReturn(client);
        doAnswer(invocation -> {
            ((ResponseListener) invocation.getArgument(1)).onFailure(downstreamFailure);
            return mock(Cancellable.class);
        }).when(client).performRequestAsync(any(Request.class), any(ResponseListener.class));
        DefaultElasticsearchOperationsViewAdapter unavailable =
                new DefaultElasticsearchOperationsViewAdapter(registry, 1000L);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> unavailable.queryDocuments(documentRequest("{\"size\":0}")));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Elasticsearch 运维查询暂不可用", exception.getMessage());
        assertSame(downstreamFailure, exception.getCause());
    }

    @Test
    void shouldRetainCauseForEmptyDocumentResponse() {
        SimpleElasticsearchRouteRegistry registry = mock(SimpleElasticsearchRouteRegistry.class);
        RestClient client = mock(RestClient.class);
        Response response = mock(Response.class);
        when(registry.getClusterInfo("search-primary")).thenReturn(mock(ClusterInfo.class));
        when(registry.getLowLevelClient("search-primary")).thenReturn(client);
        when(response.getEntity()).thenReturn(null);
        doAnswer(invocation -> {
            ((ResponseListener) invocation.getArgument(1)).onSuccess(response);
            return mock(Cancellable.class);
        }).when(client).performRequestAsync(any(Request.class), any(ResponseListener.class));
        DefaultElasticsearchOperationsViewAdapter emptyResponse =
                new DefaultElasticsearchOperationsViewAdapter(registry, 1000L);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> emptyResponse.queryDocuments(documentRequest("{\"size\":0}")));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Elasticsearch 运维查询暂不可用", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    private ElasticsearchDocumentQueryRequest documentRequest(String dsl) {
        return documentRequest("orders", dsl);
    }

    private ElasticsearchDocumentQueryRequest documentRequest(String index, String dsl) {
        return ElasticsearchDocumentQueryRequest.builder().datasourceKey("search-primary").index(index).dsl(dsl).build();
    }

    private RequestCapture requestCapture(String responseBody) {
        SimpleElasticsearchRouteRegistry registry = mock(SimpleElasticsearchRouteRegistry.class);
        RestClient client = mock(RestClient.class);
        Response response = mock(Response.class);
        HttpEntity entity = mock(HttpEntity.class);
        AtomicReference<Request> request = new AtomicReference<>();
        when(registry.getClusterInfo("search-primary")).thenReturn(mock(ClusterInfo.class));
        when(registry.getLowLevelClient("search-primary")).thenReturn(client);
        when(response.getEntity()).thenReturn(entity);
        try {
            when(entity.getContent()).thenReturn(content(responseBody));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        doAnswer(invocation -> {
            request.set(invocation.getArgument(0));
            ResponseListener listener = invocation.getArgument(1);
            listener.onSuccess(response);
            return mock(Cancellable.class);
        }).when(client).performRequestAsync(any(Request.class), any(ResponseListener.class));
        return new RequestCapture(registry, request);
    }

    private String entityText(HttpEntity entity) throws IOException {
        try (InputStream input = entity.getContent()) {
            byte[] bytes = new byte[4096];
            StringBuilder result = new StringBuilder();
            int length;
            while ((length = input.read(bytes)) != -1) {
                result.append(new String(bytes, 0, length, StandardCharsets.UTF_8));
            }
            return result.toString();
        }
    }

    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private ByteArrayInputStream content(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> names(ElasticsearchFieldCapabilitiesResponse response) {
        List<String> result = new ArrayList<>();
        for (ElasticsearchFieldCapabilitiesResponse.Item item : response.getItems()) {
            result.add(item.getName());
        }
        return result;
    }

    private List<String> asList(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    private static class RequestCapture {

        private final SimpleElasticsearchRouteRegistry registry;
        private final AtomicReference<Request> request;
        private final DefaultElasticsearchOperationsViewAdapter adapter;

        private RequestCapture(SimpleElasticsearchRouteRegistry registry, AtomicReference<Request> request) {
            this.registry = registry;
            this.request = request;
            this.adapter = new DefaultElasticsearchOperationsViewAdapter(registry, 1000L);
        }
    }
}
