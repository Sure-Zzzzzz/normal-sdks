package io.github.surezzzzzz.sdk.prometheus.route.test.cases;

import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteHttpMethod;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteRequest;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteResponse;
import io.github.surezzzzzz.sdk.prometheus.route.registry.SimplePrometheusRouteRegistry;
import io.github.surezzzzzz.sdk.prometheus.route.resolver.DefaultPrometheusRouteResolver;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.transport.DefaultPrometheusRouteTransportFactory;
import io.github.surezzzzzz.sdk.prometheus.route.validator.DefaultPrometheusRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证单 target 连接池的并发边界。
 *
 * @author surezzzzzz
 */
@Slf4j
class PrometheusRouteConcurrencyTest {

    @Test
    void rejectsPoolExhaustionAndReusesReleasedConnection() throws Exception {
        CountDownLatch firstRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        AtomicInteger handledRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            int requestNumber = handledRequests.incrementAndGet();
            if (requestNumber == 1) {
                firstRequestEntered.countDown();
                try {
                    releaseFirstRequest.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        SimplePrometheusRouteRegistry registry = null;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            SimplePrometheusRouteProperties properties = properties(
                    "http://127.0.0.1:" + server.getAddress().getPort());
            SimplePrometheusRouteProperties.HttpConfig http =
                    properties.getTargets().get("test-main").getHttp();
            http.setMaxTotal(1);
            http.setMaxPerRoute(1);
            http.setConnectionRequestTimeoutMs(100);
            http.setSocketTimeoutMs(3000);
            registry = new SimplePrometheusRouteRegistry(properties,
                    new DefaultPrometheusRoutePropertiesValidator(),
                    new DefaultPrometheusRouteTransportFactory());
            PrometheusRouteTemplate template = new PrometheusRouteTemplate(
                    registry, new DefaultPrometheusRouteResolver(registry));

            Future<PrometheusRouteResponse> first = executor.submit(
                    () -> template.exchange("test-main", request()));
            assertTrue(firstRequestEntered.await(2, TimeUnit.SECONDS),
                    "第一个请求应先占用连接池连接");

            Future<PrometheusRouteResponse> second = executor.submit(
                    () -> template.exchange("test-main", request()));
            ExecutionException execution = null;
            try {
                second.get(2, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                execution = exception;
            } catch (TimeoutException exception) {
                second.cancel(true);
            }
            assertNotNull(execution, "连接池耗尽时第二个请求应失败");
            assertTrue(execution.getCause() instanceof PrometheusRouteException,
                    "连接池超时应映射为 Route 异常");
            PrometheusRouteException routeException = (PrometheusRouteException) execution.getCause();
            log.info("连接池耗尽错误码: {}", routeException.getErrorCode());
            assertEquals(ErrorCode.REQUEST_EXECUTION_FAILED, routeException.getErrorCode(),
                    "连接池获取超时应映射为执行失败");
            assertEquals(1, handledRequests.get(), "连接池超时请求不应抵达服务端");

            releaseFirstRequest.countDown();
            PrometheusRouteResponse firstResponse = first.get(2, TimeUnit.SECONDS);
            log.info("第一个请求释放后的状态码: {}", firstResponse.getStatusCode());
            assertEquals(200, firstResponse.getStatusCode(), "已获准入请求应正常完成");

            PrometheusRouteResponse reused = template.exchange("test-main", request());
            log.info("连接复用后的状态码: {}", reused.getStatusCode());
            assertEquals(200, reused.getStatusCode(), "连接释放后应可被后续请求复用");
            assertEquals(2, handledRequests.get(), "只有成功请求应抵达服务端");
        } finally {
            releaseFirstRequest.countDown();
            if (registry != null) {
                registry.destroy();
            }
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            server.stop(0);
        }
    }

    private PrometheusRouteRequest request() {
        return new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/slow",
                Collections.emptyList(), Collections.emptyList(), null);
    }

    private SimplePrometheusRouteProperties properties(String url) {
        SimplePrometheusRouteProperties properties = new SimplePrometheusRouteProperties();
        properties.setEnable(true);
        SimplePrometheusRouteProperties.TargetConfig target =
                new SimplePrometheusRouteProperties.TargetConfig();
        target.setUrl(url);
        properties.getTargets().put("test-main", target);
        return properties;
    }
}
