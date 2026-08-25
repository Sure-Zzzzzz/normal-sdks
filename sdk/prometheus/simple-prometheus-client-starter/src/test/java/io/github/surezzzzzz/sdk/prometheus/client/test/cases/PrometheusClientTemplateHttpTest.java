package io.github.surezzzzzz.sdk.prometheus.client.test.cases;

import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.prometheus.client.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.client.exception.PrometheusClientException;
import io.github.surezzzzzz.sdk.prometheus.client.template.PrometheusClientTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteAuthenticationType;
import io.github.surezzzzzz.sdk.prometheus.route.registry.SimplePrometheusRouteRegistry;
import io.github.surezzzzzz.sdk.prometheus.route.resolver.DefaultPrometheusRouteResolver;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.transport.DefaultPrometheusRouteTransportFactory;
import io.github.surezzzzzz.sdk.prometheus.route.validator.DefaultPrometheusRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.xerial.snappy.Snappy;
import prometheus.Remote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prometheus Client 真实 HTTP 协议测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class PrometheusClientTemplateHttpTest {

    private static final String TARGET_KEY = "http-fixture";
    private static final String WRITE_PATH = "/api/v1/write";
    private static final String QUERY_PATH = "/api/v1/query";
    private static final String QUERY_RANGE_PATH = "/api/v1/query_range";

    @Test
    void clientUsesRouteForWriteQueryAndRangeRequests() throws Exception {
        AtomicReference<String> writeMethod = new AtomicReference<String>();
        AtomicReference<String> writePath = new AtomicReference<String>();
        AtomicReference<String> writeContentType = new AtomicReference<String>();
        AtomicReference<String> writeContentEncoding = new AtomicReference<String>();
        AtomicReference<String> writeVersion = new AtomicReference<String>();
        AtomicBoolean writeAuthorizationMatches = new AtomicBoolean();
        AtomicReference<String> queryMethod = new AtomicReference<String>();
        AtomicReference<String> queryString = new AtomicReference<String>();
        AtomicBoolean queryAuthorizationMatches = new AtomicBoolean();
        AtomicReference<String> rangeString = new AtomicReference<String>();
        AtomicReference<Throwable> handlerFailure = new AtomicReference<Throwable>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(WRITE_PATH, exchange -> {
            try {
                writeMethod.set(exchange.getRequestMethod());
                writePath.set(exchange.getRequestURI().getPath());
                writeContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                writeContentEncoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
                writeVersion.set(exchange.getRequestHeaders().getFirst("X-Prometheus-Remote-Write-Version"));
                writeAuthorizationMatches.set(("Basic " + basicCredential()).equals(
                        exchange.getRequestHeaders().getFirst("Authorization")));
                Remote.WriteRequest parsed = Remote.WriteRequest.parseFrom(
                        Snappy.uncompress(readBody(exchange)));
                assertEquals(0, parsed.getTimeseriesCount());
                exchange.sendResponseHeaders(204, -1L);
            } catch (Throwable throwable) {
                handlerFailure.set(throwable);
                exchange.sendResponseHeaders(500, -1L);
            } finally {
                exchange.close();
            }
        });
        server.createContext(QUERY_PATH, exchange -> {
            queryMethod.set(exchange.getRequestMethod());
            queryString.set(exchange.getRequestURI().getRawQuery());
            queryAuthorizationMatches.set(("Basic " + basicCredential()).equals(
                    exchange.getRequestHeaders().getFirst("Authorization")));
            writeJson(exchange, 200,
                    "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}");
        });
        server.createContext(QUERY_RANGE_PATH, exchange -> {
            rangeString.set(exchange.getRequestURI().getRawQuery());
            writeJson(exchange, 200,
                    "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}");
        });
        server.start();

        SimplePrometheusRouteRegistry registry = null;
        try {
            registry = createRegistry(server, PrometheusRouteAuthenticationType.BASIC);
            PrometheusClientTemplate client = new PrometheusClientTemplate(createTemplate(registry));

            client.write(TARGET_KEY, Remote.WriteRequest.getDefaultInstance());
            client.query(TARGET_KEY, "up", null);
            client.queryRange(TARGET_KEY, "up", java.time.Instant.ofEpochSecond(2),
                    java.time.Instant.ofEpochSecond(3), 15);

            log.info("验证 Client 经真实 Route 完成 write/query/queryRange 协议请求");
            assertNull(handlerFailure.get());
            assertEquals("POST", writeMethod.get());
            assertEquals(WRITE_PATH, writePath.get());
            assertEquals("application/x-protobuf", writeContentType.get());
            assertEquals("snappy", writeContentEncoding.get());
            assertEquals("0.1.0", writeVersion.get());
            assertTrue(writeAuthorizationMatches.get());
            assertEquals("GET", queryMethod.get());
            assertEquals("query=up", queryString.get());
            assertTrue(queryAuthorizationMatches.get());
            assertEquals("query=up&start=2.000&end=3.000&step=15", rangeString.get());
        } finally {
            if (registry != null) {
                registry.destroy();
            }
            server.stop(0);
        }
    }

    @Test
    void httpFailureIsMappedOnceWithoutClientRetry() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(QUERY_PATH, exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(503, -1L);
            exchange.close();
        });
        server.start();

        SimplePrometheusRouteRegistry registry = null;
        try {
            registry = createRegistry(server, PrometheusRouteAuthenticationType.NONE);
            PrometheusClientTemplate client = new PrometheusClientTemplate(createTemplate(registry));

            PrometheusClientException exception = assertThrows(PrometheusClientException.class,
                    () -> client.query(TARGET_KEY, "up", null));
            log.info("验证真实 HTTP 503 只经 Route 发送一次");
            assertEquals(ErrorCode.QUERY_FAILED, exception.getErrorCode());
            assertEquals(1, requestCount.get());
        } finally {
            if (registry != null) {
                registry.destroy();
            }
            server.stop(0);
        }
    }

    private SimplePrometheusRouteRegistry createRegistry(
            HttpServer server, PrometheusRouteAuthenticationType authenticationType) {
        SimplePrometheusRouteProperties properties = new SimplePrometheusRouteProperties();
        properties.setEnable(true);
        SimplePrometheusRouteProperties.TargetConfig target =
                new SimplePrometheusRouteProperties.TargetConfig();
        target.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
        target.getAuthentication().setType(authenticationType);
        if (authenticationType == PrometheusRouteAuthenticationType.BASIC) {
            target.getAuthentication().setUsername("fixture-user");
            target.getAuthentication().setPassword("fixture-password");
        }
        properties.getTargets().put(TARGET_KEY, target);
        return new SimplePrometheusRouteRegistry(properties,
                new DefaultPrometheusRoutePropertiesValidator(),
                new DefaultPrometheusRouteTransportFactory());
    }

    private PrometheusRouteTemplate createTemplate(SimplePrometheusRouteRegistry registry) {
        return new PrometheusRouteTemplate(registry, new DefaultPrometheusRouteResolver(registry));
    }

    private String basicCredential() {
        return java.util.Base64.getEncoder().encodeToString(
                "fixture-user:fixture-password".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] readBody(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = exchange.getRequestBody().read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void writeJson(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String value)
            throws IOException {
        byte[] body = value.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
