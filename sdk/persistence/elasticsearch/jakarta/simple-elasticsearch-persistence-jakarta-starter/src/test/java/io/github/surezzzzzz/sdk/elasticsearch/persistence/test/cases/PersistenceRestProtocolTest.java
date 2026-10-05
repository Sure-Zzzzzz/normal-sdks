package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceRestException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.JacksonPersistencePayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceRestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 仅核验 REST 线协议的本机随机端口 fixture，不替代 ES 端到端测试。
 */
@Slf4j
class PersistenceRestProtocolTest {
    @Test
    void bulkUsesNdjsonAndConsumesExactBody() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"took\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try (RestClient client = RestClient.builder(new HttpHost("127.0.0.1", server.getAddress().getPort())).build()) {
            SimpleElasticsearchRouteRegistry registry = mock(SimpleElasticsearchRouteRegistry.class);
            when(registry.getLowLevelClient("sample")).thenReturn(client);
            PersistenceRestHelper rest = new PersistenceRestHelper(registry, new JacksonPersistencePayloadCodec());
            String ndjson = "{\"index\":{\"_index\":\"sample-record\"}}\n{\"amount\":1}\n";
            assertEquals(0, rest.perform("sample", "POST", "/_bulk", Map.of(), ndjson).get("took"));
            assertTrue(contentType.get().startsWith("application/x-ndjson"));
            assertEquals(ndjson, body.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void documentAndIndex404RemainDistinctAndSanitized() throws Exception {
        AtomicReference<String> response = new AtomicReference<>("{\"_index\":\"sample-record\",\"_id\":\"row1\",\"result\":\"not_found\"}");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try (RestClient client = RestClient.builder(new HttpHost("127.0.0.1", server.getAddress().getPort())).build()) {
            SimpleElasticsearchRouteRegistry registry = mock(SimpleElasticsearchRouteRegistry.class);
            when(registry.getLowLevelClient("sample")).thenReturn(client);
            PersistenceRestHelper rest = new PersistenceRestHelper(registry, new JacksonPersistencePayloadCodec());
            PersistenceRestException missing = assertThrows(PersistenceRestException.class, () -> rest.perform("sample", "DELETE", "/sample-record/_doc/row1", Map.of(), null));
            assertTrue(missing.isDocumentMissing());
            assertNull(missing.getCause());
            response.set("{\"error\":{\"type\":\"index_not_found_exception\",\"reason\":\"private-detail\"}}");
            PersistenceRestException absent = assertThrows(PersistenceRestException.class, () -> rest.perform("sample", "DELETE", "/sample-record/_doc/row1", Map.of(), null));
            assertFalse(absent.isDocumentMissing());
            assertEquals(404, absent.getStatus());
            assertFalse(absent.getMessage().contains("private-detail"));
            assertNull(absent.getCause());
        } finally {
            server.stop(0);
        }
    }
}
