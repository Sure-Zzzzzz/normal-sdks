package io.github.surezzzzzz.sdk.s3.route.test.cases;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectResult;
import io.github.surezzzzzz.sdk.s3.route.client.S3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.registry.SimpleS3RouteRegistry;
import io.github.surezzzzzz.sdk.s3.route.resolver.DefaultS3RouteResolver;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import io.github.surezzzzzz.sdk.s3.route.validator.DefaultS3RoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * S3 Route registry 的关闭生命周期测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleS3RouteRegistryLifecycleTest {

    @Test
    void waitsForAdmittedRequestAndRejectsRequestAfterDestroy() throws Exception {
        BlockingPutClient blockingClient = new BlockingPutClient();
        SimpleS3RouteRegistry registry = registry(blockingClient.client, 1000);
        S3RouteTemplate template = new S3RouteTemplate(registry, new DefaultS3RouteResolver(registry));
        ExecutorService operationExecutor = Executors.newSingleThreadExecutor();
        ExecutorService lifecycleExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<PutObjectResult> operation = operationExecutor.submit(() -> template.execute(
                    "test-main", client -> client.putObject("bucket-a", "key-a", new File("fixture.txt"))));
            assertTrue(blockingClient.entered.await(2, TimeUnit.SECONDS), "已准入请求应进入 client");

            Future<?> destroy = lifecycleExecutor.submit(registry::destroy);

            blockingClient.release.countDown();
            PutObjectResult result = operation.get(2, TimeUnit.SECONDS);
            log.info("已准入请求关闭前完成: {}", result != null);
            destroy.get(2, TimeUnit.SECONDS);

            S3RouteException closed = null;
            try {
                template.execute("test-main",
                        client -> client.putObject("bucket-a", "key-a", new File("fixture.txt")));
            } catch (S3RouteException exception) {
                closed = exception;
            }
            assertEquals(ErrorCode.ROUTE_CLOSED, closed.getErrorCode(),
                    "destroy 完成后新请求应在网络前被拒绝");
            assertEquals(1, blockingClient.shutdownCount.get(), "destroy 应关闭 target client 一次");
        } finally {
            blockingClient.release.countDown();
            registry.destroy();
            operationExecutor.shutdownNow();
            lifecycleExecutor.shutdownNow();
            operationExecutor.awaitTermination(2, TimeUnit.SECONDS);
            lifecycleExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void closesClientWhenShutdownDeadlineExpires() throws Exception {
        BlockingPutClient blockingClient = new BlockingPutClient();
        SimpleS3RouteRegistry registry = registry(blockingClient.client, 50);
        S3RouteTemplate template = new S3RouteTemplate(registry, new DefaultS3RouteResolver(registry));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PutObjectResult> operation = executor.submit(() -> template.execute(
                    "test-main", client -> client.putObject("bucket-a", "key-a", new File("fixture.txt"))));
            assertTrue(blockingClient.entered.await(2, TimeUnit.SECONDS), "已准入请求应进入 client");

            registry.destroy();
            assertEquals(1, blockingClient.shutdownCount.get(), "shutdown deadline 到期后应关闭 client");

            blockingClient.release.countDown();
            PutObjectResult result = operation.get(2, TimeUnit.SECONDS);
            log.info("deadline 后在途请求完成: {}", result != null);
        } finally {
            blockingClient.release.countDown();
            registry.destroy();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void factoryFailureRollsBackCreatedClients() {
        AmazonS3 created = mock(AmazonS3.class);
        S3RouteClientFactory factory = (targetKey, config) -> {
            if ("first".equals(targetKey)) {
                return created;
            }
            throw new IllegalStateException("second target bootstrap failure");
        };
        SimpleS3RouteProperties properties = new SimpleS3RouteProperties();
        properties.setEnable(true);
        properties.getTargets().put("first", target());
        properties.getTargets().put("second", target());

        S3RouteException failure = null;
        try {
            new SimpleS3RouteRegistry(properties, new DefaultS3RoutePropertiesValidator(), factory);
        } catch (S3RouteException exception) {
            failure = exception;
        }
        assertTrue(failure != null, "工厂失败应抛出配置非法异常");
        assertEquals(ErrorCode.TARGET_CONFIGURATION_ILLEGAL, failure.getErrorCode(),
                "工厂失败应转为配置非法异常");
        verify(created, times(1)).shutdown();
        log.info("工厂失败后已回滚 client shutdown 次数: 1");
    }

    private SimpleS3RouteRegistry registry(AmazonS3 client, int shutdownTimeoutMs) {
        SimpleS3RouteProperties properties = new SimpleS3RouteProperties();
        properties.setEnable(true);
        properties.setShutdownTimeoutMs(shutdownTimeoutMs);
        properties.getTargets().put("test-main", target());
        S3RouteClientFactory factory = (targetKey, config) -> client;
        return new SimpleS3RouteRegistry(properties,
                new DefaultS3RoutePropertiesValidator(), factory);
    }

    private SimpleS3RouteProperties.TargetConfig target() {
        SimpleS3RouteProperties.TargetConfig target = new SimpleS3RouteProperties.TargetConfig();
        target.setEndpoint("http://127.0.0.1:19000");
        return target;
    }

    private static class BlockingPutClient {

        private final AmazonS3 client = mock(AmazonS3.class);
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger shutdownCount = new AtomicInteger();

        private BlockingPutClient() {
            Answer<PutObjectResult> blocking = invocation -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return new PutObjectResult();
            };
            when(client.putObject(anyString(), anyString(), any(File.class)))
                    .thenAnswer(blocking);
            doAnswer(invocation -> {
                shutdownCount.incrementAndGet();
                return null;
            }).when(client).shutdown();
        }
    }
}
