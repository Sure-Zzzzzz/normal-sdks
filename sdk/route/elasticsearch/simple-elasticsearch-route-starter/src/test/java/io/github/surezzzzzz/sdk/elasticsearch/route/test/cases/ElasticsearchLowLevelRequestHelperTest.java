package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchLowLevelRequestHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchLowLevelRequestHelperTest {

    @Test
    public void testRequestFactory() throws Exception {
        log.info("=== testRequestFactory ===");
        Request request = ElasticsearchLowLevelRequestHelper.newJsonRequest("POST", "/test_index/_search", "{\"query\":{}} ");
        assertEquals("POST", request.getMethod());
        assertEquals("/test_index/_search", request.getEndpoint());
        assertEquals("{\"query\":{}} ", ElasticsearchLowLevelRequestHelper.readEntity(request.getEntity()));
    }

    @Test
    public void testAddParametersAndAggregationDetect() throws Exception {
        log.info("=== testAddParametersAndAggregationDetect ===");
        Request request = ElasticsearchLowLevelRequestHelper.newRequest("GET", "/test_index/_mapping");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ignore_unavailable", "true");
        ElasticsearchLowLevelRequestHelper.addParameters(request, params);
        ElasticsearchLowLevelRequestHelper.applyInitialScrollParam(request, "1m");

        assertEquals("true", request.getParameters().get("ignore_unavailable"));
        assertEquals("1m", request.getParameters().get("scroll"));
        assertEquals("{\"query\":{}}", ElasticsearchLowLevelRequestHelper.readEntity(
                new StringEntity("{\"query\":{}}")));
        assertTrue(ElasticsearchLowLevelRequestHelper.containsAggregations("{\"aggregations\":{}}"));
        assertFalse(ElasticsearchLowLevelRequestHelper.containsAggregations("{\"hits\":{}}"));
    }

    @Test
    public void testBuildIndexAndDocRequests() throws Exception {
        log.info("=== testBuildIndexAndDocRequests ===");
        ClusterInfo es6 = ClusterInfo.initial("primary", ServerVersion.parse("6.2.2"));
        ClusterInfo es7 = ClusterInfo.initial("primary", ServerVersion.parse("7.17.9"));
        String mapping = "{\"properties\":{\"title\":{\"type\":\"keyword\"}}}";
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("title", "a");
        source.put("day", LocalDate.of(2026, 7, 9));

        Request es6Create = ElasticsearchLowLevelRequestHelper.buildCreateIndexRequest("test_index", mapping, es6);
        Request es7Create = ElasticsearchLowLevelRequestHelper.buildCreateIndexRequest("test_index", mapping, es7);
        Request indexDoc = ElasticsearchLowLevelRequestHelper.buildIndexDocRequest("test_index", "1", source);
        Request deleteDoc = ElasticsearchLowLevelRequestHelper.buildDeleteDocRequest("test_index", "1");
        Request refresh = ElasticsearchLowLevelRequestHelper.buildRefreshRequest("test_index");

        assertEquals("PUT", es6Create.getMethod());
        assertEquals("/test_index", es6Create.getEndpoint());
        assertEquals("{\"mappings\":{\"_doc\":{\"properties\":{\"title\":{\"type\":\"keyword\"}}}}}",
                ElasticsearchLowLevelRequestHelper.readEntity(es6Create.getEntity()));
        assertEquals("{\"mappings\":{\"properties\":{\"title\":{\"type\":\"keyword\"}}}}",
                ElasticsearchLowLevelRequestHelper.readEntity(es7Create.getEntity()));
        assertEquals("PUT", indexDoc.getMethod());
        assertEquals("/test_index/_doc/1", indexDoc.getEndpoint());
        assertEquals("true", indexDoc.getParameters().get("refresh"));
        assertEquals("{\"title\":\"a\",\"day\":\"2026-07-09\"}",
                ElasticsearchLowLevelRequestHelper.readEntity(indexDoc.getEntity()));
        assertEquals("DELETE", deleteDoc.getMethod());
        assertEquals("/test_index/_doc/1", deleteDoc.getEndpoint());
        assertEquals("true", deleteDoc.getParameters().get("refresh"));
        assertEquals("POST", refresh.getMethod());
        assertEquals("/test_index/_refresh", refresh.getEndpoint());
    }

    @Test
    public void testRestClientProtocolHelpers() throws Exception {
        log.info("=== testRestClientProtocolHelpers ===");
        List<RecordedRequest> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> handle(exchange, requests));
        server.start();
        RestClient client = RestClient.builder(new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")).build();
        try {
            assertTrue(ElasticsearchLowLevelRequestHelper.indexExists(client, "exists_index"));
            assertFalse(ElasticsearchLowLevelRequestHelper.indexExists(client, "missing_index"));

            Map<String, Object> source = ElasticsearchLowLevelRequestHelper.getDoc(client, "test_index", "1");
            assertNotNull(source);
            assertEquals("doc-1", source.get("title"));
            assertEquals(1, source.get("count"));
            assertNull(ElasticsearchLowLevelRequestHelper.getDoc(client, "test_index", "404"));
            assertTrue(ElasticsearchLowLevelRequestHelper.docExists(client, "test_index", "1"));
            assertFalse(ElasticsearchLowLevelRequestHelper.docExists(client, "test_index", "404"));

            ElasticsearchLowLevelRequestHelper.indexDoc(client, "test_index", "2", source);
            ElasticsearchLowLevelRequestHelper.deleteDoc(client, "test_index", "404");
            ElasticsearchLowLevelRequestHelper.deleteIndex(client, "missing_index");
            ElasticsearchLowLevelRequestHelper.refresh(client, "test_index");
        } finally {
            client.close();
            server.stop(0);
        }

        assertRecorded(requests, "HEAD", "/exists_index", null);
        assertRecorded(requests, "HEAD", "/missing_index", null);
        assertRecorded(requests, "GET", "/test_index/_doc/1", null);
        assertRecorded(requests, "GET", "/test_index/_doc/404", null);
        assertRecorded(requests, "HEAD", "/test_index/_doc/1", null);
        assertRecorded(requests, "HEAD", "/test_index/_doc/404", null);
        assertRecorded(requests, "PUT", "/test_index/_doc/2?refresh=true", "{\"title\":\"doc-1\",\"count\":1}");
        assertRecorded(requests, "DELETE", "/test_index/_doc/404?refresh=true", null);
        assertRecorded(requests, "DELETE", "/missing_index", null);
        assertRecorded(requests, "POST", "/test_index/_refresh", null);
    }

    private static void handle(HttpExchange exchange, List<RecordedRequest> requests) throws IOException {
        String body = readBody(exchange);
        String path = exchange.getRequestURI().toString();
        requests.add(new RecordedRequest(exchange.getRequestMethod(), path, body.isEmpty() ? null : body));
        int status = status(exchange.getRequestMethod(), path);
        byte[] response = responseBody(exchange.getRequestMethod(), path).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, "HEAD".equals(exchange.getRequestMethod()) ? -1 : response.length);
        if (!"HEAD".equals(exchange.getRequestMethod())) {
            exchange.getResponseBody().write(response);
        }
        exchange.close();
    }

    private static int status(String method, String path) {
        if (path.contains("missing_index") || path.endsWith("/_doc/404") || path.contains("/_doc/404?")) {
            return 404;
        }
        return 200;
    }

    private static String responseBody(String method, String path) {
        if ("GET".equals(method) && path.endsWith("/_doc/1")) {
            return "{\"_source\":{\"title\":\"doc-1\",\"count\":1}}";
        }
        return "{}";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[256];
        int read;
        while ((read = exchange.getRequestBody().read(data)) != -1) {
            buffer.write(data, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void assertRecorded(List<RecordedRequest> requests, String method, String path, String body) {
        for (RecordedRequest request : requests) {
            if (method.equals(request.method) && path.equals(request.path)
                    && (body == null ? request.body == null : body.equals(request.body))) {
                return;
            }
        }
        fail("未找到请求：" + method + " " + path + " body=" + body);
    }

    private static class RecordedRequest {
        private final String method;
        private final String path;
        private final String body;

        private RecordedRequest(String method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }
    }
}
