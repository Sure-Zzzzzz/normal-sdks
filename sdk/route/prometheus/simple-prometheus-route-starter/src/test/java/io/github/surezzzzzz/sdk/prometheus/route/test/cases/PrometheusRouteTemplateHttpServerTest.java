package io.github.surezzzzzz.sdk.prometheus.route.test.cases;

import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.*;
import io.github.surezzzzzz.sdk.prometheus.route.registry.SimplePrometheusRouteRegistry;
import io.github.surezzzzzz.sdk.prometheus.route.resolver.DefaultPrometheusRouteResolver;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.transport.DefaultPrometheusRouteTransportFactory;
import io.github.surezzzzzz.sdk.prometheus.route.validator.DefaultPrometheusRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class PrometheusRouteTemplateHttpServerTest {

    @Test
    void sendsOnlyToRegisteredTargetAndReturnsSnapshot() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<String>();
        AtomicReference<String> authorization = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/prometheus/api/v1/query", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Test", "response");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        SimplePrometheusRouteRegistry registry = null;
        try {
            SimplePrometheusRouteProperties properties = properties("http://127.0.0.1:" + server.getAddress().getPort() + "/prometheus");
            registry = new SimplePrometheusRouteRegistry(properties, new DefaultPrometheusRoutePropertiesValidator(),
                    new DefaultPrometheusRouteTransportFactory());
            PrometheusRouteTemplate template = new PrometheusRouteTemplate(registry, new DefaultPrometheusRouteResolver(registry));
            PrometheusRouteResponse response = template.exchange("test-main", new PrometheusRouteRequest(
                    PrometheusRouteHttpMethod.GET, "/api/v1/query",
                    Collections.singletonList(new PrometheusRouteParameter("query", "up")),
                    Collections.<PrometheusRouteHeader>emptyList(), null));

            log.info("固定 base path 请求状态码: {}，响应正文长度: {}",
                    response.getStatusCode(), response.getBody().length);
            assertEquals(200, response.getStatusCode());
            assertTrue("/prometheus/api/v1/query?query=up".equals(requestPath.get()),
                    "固定 base path 合并结果不匹配");
            assertTrue("Basic dXNlcjpwYXNz".equals(authorization.get()), "认证 header 不匹配");
            assertTrue(Arrays.equals("{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8), response.getBody()),
                    "响应正文快照不匹配");
        } finally {
            if (registry != null) {
                registry.destroy();
            }
            server.stop(0);
        }
    }

    @Test
    void bearerAuthenticationAndPostHeadersAreIsolated() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<String>();
        AtomicReference<String> contentType = new AtomicReference<String>();
        AtomicReference<String> body = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            java.io.ByteArrayOutputStream received = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[64];
            int count;
            while ((count = exchange.getRequestBody().read(buffer)) >= 0) {
                received.write(buffer, 0, count);
            }
            body.set(new String(received.toByteArray(), StandardCharsets.UTF_8));
            byte[] response = "{\"status\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        SimplePrometheusRouteRegistry registry = null;
        try {
            SimplePrometheusRouteProperties properties = properties("http://127.0.0.1:" + server.getAddress().getPort());
            SimplePrometheusRouteProperties.AuthenticationConfig authentication =
                    properties.getTargets().get("test-main").getAuthentication();
            authentication.setType(PrometheusRouteAuthenticationType.BEARER);
            authentication.setUsername(null);
            authentication.setPassword(null);
            authentication.setToken("token-value");
            registry = new SimplePrometheusRouteRegistry(properties, new DefaultPrometheusRoutePropertiesValidator(),
                    new DefaultPrometheusRouteTransportFactory());
            PrometheusRouteTemplate template = new PrometheusRouteTemplate(registry, new DefaultPrometheusRouteResolver(registry));
            PrometheusRouteResponse response = template.exchange("test-main", new PrometheusRouteRequest(
                    PrometheusRouteHttpMethod.POST, "/api/v1/query", null,
                    Collections.singletonList(new PrometheusRouteHeader("Content-Type", "application/x-www-form-urlencoded")),
                    "query=up".getBytes(StandardCharsets.UTF_8)));
            log.info("Bearer POST 请求状态码: {}，请求正文长度: {}", response.getStatusCode(), body.get().length());
            assertEquals(200, response.getStatusCode());
            assertTrue("Bearer token-value".equals(authorization.get()), "认证 header 不匹配");
            assertTrue("application/x-www-form-urlencoded".equals(contentType.get()), "Content-Type 不匹配");
            assertTrue("query=up".equals(body.get()), "请求正文不匹配");
        } finally {
            if (registry != null) {
                registry.destroy();
            }
            server.stop(0);
        }
    }

    @Test
    void transportFailureIsNotRetried() throws Exception {
        AtomicInteger receivedRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/connection-failure", exchange -> {
            receivedRequests.incrementAndGet();
            exchange.close();
        });
        server.start();
        SimplePrometheusRouteRegistry registry = new SimplePrometheusRouteRegistry(
                properties("http://127.0.0.1:" + server.getAddress().getPort()),
                new DefaultPrometheusRoutePropertiesValidator(), new DefaultPrometheusRouteTransportFactory());
        try {
            PrometheusRouteTemplate template = new PrometheusRouteTemplate(registry,
                    new DefaultPrometheusRouteResolver(registry));
            PrometheusRouteException exception = assertThrows(PrometheusRouteException.class,
                    () -> template.exchange("test-main", new PrometheusRouteRequest(
                            PrometheusRouteHttpMethod.GET, "/connection-failure", null, null, null)));

            log.info("连接中断错误码: {}，服务端接收次数: {}", exception.getErrorCode(), receivedRequests.get());
            assertEquals(ErrorCode.REQUEST_EXECUTION_FAILED, exception.getErrorCode());
            assertEquals(1, receivedRequests.get());
        } finally {
            registry.destroy();
            server.stop(0);
        }
    }

    @Test
    void responseBodyLimitAndRedirectAreEnforced() throws Exception {
        AtomicInteger redirectedRequests = new AtomicInteger();
        AtomicInteger tooLargeRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final");
            exchange.sendResponseHeaders(302, 0);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/too-large", exchange -> {
            tooLargeRequests.incrementAndGet();
            byte[] body = "large".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/at-limit", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        SimplePrometheusRouteProperties properties = properties("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getTargets().get("test-main").getHttp().setMaxResponseBodyBytes(2);
        SimplePrometheusRouteRegistry registry = new SimplePrometheusRouteRegistry(properties,
                new DefaultPrometheusRoutePropertiesValidator(), new DefaultPrometheusRouteTransportFactory());
        try {
            PrometheusRouteTemplate template = new PrometheusRouteTemplate(registry, new DefaultPrometheusRouteResolver(registry));
            PrometheusRouteResponse redirect = template.exchange("test-main", new PrometheusRouteRequest(
                    PrometheusRouteHttpMethod.GET, "/redirect", null, null, null));
            log.info("重定向响应状态码: {}，自动跟随次数: {}", redirect.getStatusCode(), redirectedRequests.get());
            assertEquals(302, redirect.getStatusCode());
            assertEquals(0, redirectedRequests.get());
            PrometheusRouteResponse atLimit = template.exchange("test-main", new PrometheusRouteRequest(
                    PrometheusRouteHttpMethod.GET, "/at-limit", null, null, null));
            log.info("响应正文上限边界状态码: {}，正文长度: {}", atLimit.getStatusCode(), atLimit.getBody().length);
            assertTrue(Arrays.equals("ok".getBytes(StandardCharsets.UTF_8), atLimit.getBody()),
                    "响应正文上限边界快照不匹配");
            PrometheusRouteException tooLarge = assertThrows(PrometheusRouteException.class, () ->
                    template.exchange("test-main", new PrometheusRouteRequest(
                            PrometheusRouteHttpMethod.GET, "/too-large", null, null, null)));
            log.info("响应正文超限错误码: {}，服务端接收次数: {}", tooLarge.getErrorCode(), tooLargeRequests.get());
            assertEquals(ErrorCode.RESPONSE_BODY_EXCEEDS_LIMIT, tooLarge.getErrorCode());
            assertEquals(1, tooLargeRequests.get());
        } finally {
            registry.destroy();
            server.stop(0);
        }
    }

    @Test
    void rejectsCallerAuthorizationAndClosedRoute() {
        SimplePrometheusRouteProperties properties = properties("http://127.0.0.1:1");
        SimplePrometheusRouteRegistry registry = new SimplePrometheusRouteRegistry(properties,
                new DefaultPrometheusRoutePropertiesValidator(), new DefaultPrometheusRouteTransportFactory());
        PrometheusRouteTemplate template = new PrometheusRouteTemplate(registry, new DefaultPrometheusRouteResolver(registry));
        try {
            PrometheusRouteException header = assertThrows(PrometheusRouteException.class, () -> template.exchange("test-main",
                    new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/api/v1/query", null,
                            Collections.singletonList(new PrometheusRouteHeader("Authorization", "x")), null)));
            log.info("受控 header 覆盖错误码: {}", header.getErrorCode());
            assertEquals(ErrorCode.REQUEST_ILLEGAL, header.getErrorCode());
            registry.destroy();
            PrometheusRouteException closed = assertThrows(PrometheusRouteException.class, () -> template.exchange("test-main",
                    new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/api/v1/query", null, null, null)));
            log.info("Route 关闭错误码: {}", closed.getErrorCode());
            assertEquals(ErrorCode.ROUTE_CLOSED, closed.getErrorCode());
        } finally {
            registry.destroy();
        }
    }

    @Test
    void executionExceptionDoesNotExposeTargetOrEndpoint() {
        String targetKey = "internal-target";
        String endpoint = "http://127.0.0.1:1";
        SimplePrometheusRouteProperties properties = properties(endpoint);
        properties.getTargets().clear();
        properties.getTargets().put(targetKey, properties(endpoint).getTargets().get("test-main"));
        SimplePrometheusRouteRegistry registry = new SimplePrometheusRouteRegistry(properties,
                new DefaultPrometheusRoutePropertiesValidator(), new DefaultPrometheusRouteTransportFactory());
        try {
            PrometheusRouteTemplate template = new PrometheusRouteTemplate(registry,
                    new DefaultPrometheusRouteResolver(registry));

            PrometheusRouteException exception = assertThrows(PrometheusRouteException.class,
                    () -> template.exchange(targetKey, new PrometheusRouteRequest(
                            PrometheusRouteHttpMethod.GET, "/api/v1/query", null, null, null)));

            log.info("连接失败错误码: {}", exception.getErrorCode());
            assertEquals(ErrorCode.REQUEST_EXECUTION_FAILED, exception.getErrorCode());
            assertEquals(ErrorMessage.REQUEST_EXECUTION_FAILED, exception.getMessage());
            assertFalse(exception.getMessage().contains(targetKey));
            assertFalse(exception.getMessage().contains(endpoint));
        } finally {
            registry.destroy();
        }
    }

    private SimplePrometheusRouteProperties properties(String url) {
        SimplePrometheusRouteProperties properties = new SimplePrometheusRouteProperties();
        properties.setEnable(true);
        SimplePrometheusRouteProperties.TargetConfig target = new SimplePrometheusRouteProperties.TargetConfig();
        target.setUrl(url);
        target.getAuthentication().setType(PrometheusRouteAuthenticationType.BASIC);
        target.getAuthentication().setUsername("user");
        target.getAuthentication().setPassword("pass");
        properties.getTargets().put("test-main", target);
        return properties;
    }
}
