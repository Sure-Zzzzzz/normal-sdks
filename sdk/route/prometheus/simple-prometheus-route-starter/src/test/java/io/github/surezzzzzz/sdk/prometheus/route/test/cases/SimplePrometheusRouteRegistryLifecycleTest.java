package io.github.surezzzzzz.sdk.prometheus.route.test.cases;

import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteHttpMethod;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteRequest;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteResponse;
import io.github.surezzzzzz.sdk.prometheus.route.registry.SimplePrometheusRouteRegistry;
import io.github.surezzzzzz.sdk.prometheus.route.resolver.DefaultPrometheusRouteResolver;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteTransport;
import io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteTransportFactory;
import io.github.surezzzzzz.sdk.prometheus.route.validator.DefaultPrometheusRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Route registry 的关闭生命周期。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimplePrometheusRouteRegistryLifecycleTest {

    @Test
    void waitsForAdmittedRequestAndRejectsRequestAfterDestroy() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        SimplePrometheusRouteRegistry registry = registry(transport, 1000);
        PrometheusRouteTemplate template = new PrometheusRouteTemplate(
                registry, new DefaultPrometheusRouteResolver(registry));
        ExecutorService exchangeExecutor = Executors.newSingleThreadExecutor();
        ExecutorService lifecycleExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<PrometheusRouteResponse> exchange = exchangeExecutor.submit(
                    () -> template.exchange("test-main", request()));
            assertTrue(transport.entered.await(2, TimeUnit.SECONDS), "已准入请求应进入 transport");

            Future<?> destroy = lifecycleExecutor.submit(registry::destroy);

            transport.release.countDown();
            PrometheusRouteResponse response = exchange.get(2, TimeUnit.SECONDS);
            log.info("已准入请求关闭前完成状态码: {}", response.getStatusCode());
            assertEquals(200, response.getStatusCode(), "已准入请求应正常完成");
            destroy.get(2, TimeUnit.SECONDS);

            PrometheusRouteException closed = null;
            try {
                template.exchange("test-main", request());
            } catch (PrometheusRouteException exception) {
                closed = exception;
            }
            assertEquals(ErrorCode.ROUTE_CLOSED, closed.getErrorCode(),
                    "destroy 完成后新请求应在网络前被拒绝");
            assertEquals(1, transport.closeCount.get(), "destroy 应关闭 target transport 一次");
        } finally {
            transport.release.countDown();
            registry.destroy();
            exchangeExecutor.shutdownNow();
            lifecycleExecutor.shutdownNow();
            exchangeExecutor.awaitTermination(2, TimeUnit.SECONDS);
            lifecycleExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void closesTransportWhenShutdownDeadlineExpires() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        SimplePrometheusRouteRegistry registry = registry(transport, 50);
        PrometheusRouteTemplate template = new PrometheusRouteTemplate(
                registry, new DefaultPrometheusRouteResolver(registry));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PrometheusRouteResponse> exchange = executor.submit(
                    () -> template.exchange("test-main", request()));
            assertTrue(transport.entered.await(2, TimeUnit.SECONDS), "已准入请求应进入 transport");

            registry.destroy();
            assertEquals(1, transport.closeCount.get(), "shutdown deadline 到期后应关闭 transport");

            transport.release.countDown();
            PrometheusRouteResponse response = exchange.get(2, TimeUnit.SECONDS);
            log.info("deadline 后在途请求完成状态码: {}", response.getStatusCode());
            assertEquals(200, response.getStatusCode(), "测试 transport 允许在途请求自行完成");
        } finally {
            transport.release.countDown();
            registry.destroy();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private SimplePrometheusRouteRegistry registry(BlockingTransport transport, int shutdownTimeoutMs) {
        SimplePrometheusRouteProperties properties = new SimplePrometheusRouteProperties();
        properties.setEnable(true);
        properties.setShutdownTimeoutMs(shutdownTimeoutMs);
        SimplePrometheusRouteProperties.TargetConfig target =
                new SimplePrometheusRouteProperties.TargetConfig();
        target.setUrl("http://127.0.0.1:9090");
        properties.getTargets().put("test-main", target);
        PrometheusRouteTransportFactory factory = (targetKey, config) -> transport;
        return new SimplePrometheusRouteRegistry(properties,
                new DefaultPrometheusRoutePropertiesValidator(), factory);
    }

    private PrometheusRouteRequest request() {
        return new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/api/v1/query",
                Collections.emptyList(), Collections.emptyList(), null);
    }

    private static class BlockingTransport implements PrometheusRouteTransport {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public PrometheusRouteResponse exchange(PrometheusRouteRequest request) {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return PrometheusRouteResponse.of(200, Collections.emptyList(), new byte[0]);
        }

        @Override
        public void close() throws IOException {
            closeCount.incrementAndGet();
        }

    }
}
