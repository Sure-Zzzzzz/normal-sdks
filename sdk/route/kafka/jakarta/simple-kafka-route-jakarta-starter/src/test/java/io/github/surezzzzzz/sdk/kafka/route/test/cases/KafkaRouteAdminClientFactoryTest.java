package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.factory.DefaultKafkaRouteAdminClientFactory;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientFactory;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import io.github.surezzzzzz.sdk.kafka.route.validator.DefaultKafkaRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route AdminClient 资源工厂测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRouteAdminClientFactoryTest {

    @Test
    public void testReturnsCallbackResultAndClosesAdminClient() {
        RecordingOperations operations = new RecordingOperations();
        KafkaRouteAdminClientFactory factory = factory(KafkaRouteTestDataHelper.properties(), operations);

        String result = factory.withAdminClient("default", adminClient -> {
            assertSame(operations.adminClient, adminClient);
            return "callback-result";
        });
        log.info("AdminClient 正常回调结果={}，createCount={}，closeCount={}", result,
                operations.createCount.get(), operations.closeCount.get());

        assertEquals("callback-result", result);
        assertEquals(1, operations.createCount.get());
        assertEquals(1, operations.closeCount.get());
        assertEquals("default-client", operations.lastProperties.get().get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID));
        assertEquals(Collections.singletonList("127.0.0.1:9092"),
                operations.lastProperties.get().get(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS));
    }

    @Test
    public void testClosesAdminClientAndPreservesCallbackFailure() {
        RecordingOperations operations = new RecordingOperations();
        KafkaRouteAdminClientFactory factory = factory(KafkaRouteTestDataHelper.properties(), operations);
        IllegalStateException expected = new IllegalStateException("callback failed");

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> factory.withAdminClient("default", adminClient -> {
                    throw expected;
                }));
        log.info("AdminClient 回调异常={}，closeCount={}", actual.getMessage(), operations.closeCount.get());

        assertSame(expected, actual);
        assertEquals(1, operations.closeCount.get());
    }

    @Test
    public void testCloseFailureDoesNotReplaceCallbackResultOrFailure() {
        RecordingOperations operations = new RecordingOperations();
        operations.closeFailure = new IllegalStateException("close failed");
        KafkaRouteAdminClientFactory factory = factory(KafkaRouteTestDataHelper.properties(), operations);

        String result = factory.withAdminClient("default", adminClient -> "callback-result");
        IllegalArgumentException expected = new IllegalArgumentException("callback failed");
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                () -> factory.withAdminClient("default", adminClient -> {
                    throw expected;
                }));
        log.info("AdminClient 关闭失败不替换结果，result={}，exception={}，closeCount={}", result,
                actual.getMessage(), operations.closeCount.get());

        assertEquals("callback-result", result);
        assertSame(expected, actual);
        assertEquals(2, operations.closeCount.get());
    }

    @Test
    public void testCreateFailureDoesNotExposeUnderlyingException() {
        RecordingOperations operations = new RecordingOperations();
        operations.createFailure = new IllegalStateException("sasl.jaas.config=secret-value");
        KafkaRouteAdminClientFactory factory = factory(KafkaRouteTestDataHelper.properties(), operations);

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factory.withAdminClient("default", adminClient -> null));
        log.info("AdminClient 创建失败错误码={}，message={}，hasCause={}", exception.getErrorCode(),
                exception.getMessage(), exception.getCause() != null);

        assertEquals(ErrorCode.KAFKA_ROUTE_006, exception.getErrorCode());
        assertNull(exception.getCause(), "创建失败不应向调用方透传底层异常链");
        assertFalse(exception.getMessage().contains("secret-value"), "异常消息不应包含底层安全配置");
        assertEquals(1, operations.createCount.get());
        assertEquals(0, operations.closeCount.get());
    }

    @Test
    public void testRejectsInvalidInputsBeforeCreatingAdminClient() {
        RecordingOperations operations = new RecordingOperations();
        KafkaRouteAdminClientFactory factory = factory(KafkaRouteTestDataHelper.properties(), operations);

        RouteException callbackException = assertThrows(RouteException.class,
                () -> factory.withAdminClient("default", null));
        RouteException emptyDatasourceException = assertThrows(RouteException.class,
                () -> factory.withAdminClient(" ", adminClient -> null));
        RouteException missingDatasourceException = assertThrows(RouteException.class,
                () -> factory.withAdminClient("missing", adminClient -> null));
        log.info("AdminClient 参数校验错误码：callback={}，empty={}，missing={}，createCount={}",
                callbackException.getErrorCode(), emptyDatasourceException.getErrorCode(),
                missingDatasourceException.getErrorCode(), operations.createCount.get());

        assertEquals(ErrorCode.KAFKA_ROUTE_010, callbackException.getErrorCode());
        assertEquals(ErrorCode.KAFKA_ROUTE_003, emptyDatasourceException.getErrorCode());
        assertEquals(ErrorCode.KAFKA_ROUTE_003, missingDatasourceException.getErrorCode());
        assertEquals(0, operations.createCount.get());
    }

    @Test
    public void testCloseErrorPropagatesAfterCallback() {
        RecordingOperations operations = new RecordingOperations();
        AssertionError expected = new AssertionError("close error");
        operations.closeError = expected;
        KafkaRouteAdminClientFactory factory = factory(KafkaRouteTestDataHelper.properties(), operations);

        AssertionError actual = assertThrows(AssertionError.class,
                () -> factory.withAdminClient("default", adminClient -> "callback-result"));
        log.info("AdminClient 关闭 Error={}，closeCount={}", actual.getMessage(), operations.closeCount.get());

        assertSame(expected, actual, "关闭 Error 必须按 JVM 语义传播");
        assertEquals(1, operations.closeCount.get());
    }

    @Test
    public void testRejectsNewCallsAfterDestroyWithoutCreatingAdminClient() {
        RecordingOperations operations = new RecordingOperations();
        DefaultKafkaRouteAdminClientFactory factory = (DefaultKafkaRouteAdminClientFactory) factory(
                KafkaRouteTestDataHelper.properties(), operations);
        factory.destroy();

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factory.withAdminClient("default", adminClient -> null));
        log.info("AdminClientFactory 关闭后错误码={}，createCount={}", exception.getErrorCode(),
                operations.createCount.get());

        assertEquals(ErrorCode.KAFKA_ROUTE_017, exception.getErrorCode());
        assertEquals(0, operations.createCount.get());
    }

    @Test
    public void testRejectsNewCallsWhileExistingCallbackCompletesAndCloses() throws Exception {
        RecordingOperations operations = new RecordingOperations();
        DefaultKafkaRouteAdminClientFactory factory = (DefaultKafkaRouteAdminClientFactory) factory(
                KafkaRouteTestDataHelper.properties(), operations);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        Thread callbackThread = new Thread(() -> {
            try {
                factory.withAdminClient("default", adminClient -> {
                    callbackEntered.countDown();
                    await(releaseCallback);
                    return null;
                });
            } catch (Throwable e) {
                callbackFailure.set(e);
            }
        });
        callbackThread.start();
        ConfigurationException exception;
        try {
            assertTrue(callbackEntered.await(5L, TimeUnit.SECONDS), "回调必须在销毁前获得 AdminClient");

            factory.destroy();
            exception = assertThrows(ConfigurationException.class,
                    () -> factory.withAdminClient("default", adminClient -> null));
            assertEquals(ErrorCode.KAFKA_ROUTE_017, exception.getErrorCode());
        } finally {
            releaseCallback.countDown();
            callbackThread.join(5000L);
        }
        log.info("销毁竞争：newCallError={}，createCount={}，closeCount={}，callbackFailure={}",
                exception.getErrorCode(), operations.createCount.get(), operations.closeCount.get(), callbackFailure.get());

        assertFalse(callbackThread.isAlive(), "已获准的回调必须能够完成");
        assertNull(callbackFailure.get(), "已获准的回调不应被 destroy 中断");
        assertEquals(1, operations.createCount.get());
        assertEquals(1, operations.closeCount.get());
    }

    @Test
    public void testUsesConstructionTimePropertiesSnapshot() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        RecordingOperations operations = new RecordingOperations();
        KafkaRouteAdminClientFactory factory = factory(properties, operations);
        properties.getSources().get("default").setClientId("changed-client");
        properties.getSources().get("default").getBootstrapServers().set(0, "127.0.0.1:9192");

        factory.withAdminClient("default", adminClient -> null);
        log.info("AdminClient 配置快照：clientId={}，bootstrapServers={}",
                operations.lastProperties.get().get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID),
                operations.lastProperties.get().get(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS));

        assertEquals("default-client", operations.lastProperties.get().get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID));
        assertEquals(Collections.singletonList("127.0.0.1:9092"),
                operations.lastProperties.get().get(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS));
    }

    @Test
    public void testCreatesIndependentPropertiesForEachAdminClient() {
        RecordingOperations operations = new RecordingOperations();
        KafkaRouteAdminClientFactory factory = factory(KafkaRouteTestDataHelper.properties(), operations);

        factory.withAdminClient("default", adminClient -> null);
        operations.receivedProperties.get().put(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID, "mutated-client");
        factory.withAdminClient("default", adminClient -> null);
        log.info("AdminClient 配置副本次数={}，secondClientId={}", operations.createCount.get(),
                operations.lastProperties.get().get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID));

        assertEquals(2, operations.createCount.get());
        assertEquals("default-client",
                operations.lastProperties.get().get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID),
                "前一次客户端对配置副本的修改不得污染后续创建");
    }

    /**
     * 等待测试线程同步点，超时即失败，避免测试静默继续。
     *
     * @param latch 同步闩锁
     */
    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5L, TimeUnit.SECONDS), "测试回调必须在超时前收到释放信号");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("测试线程等待释放信号时被中断");
        }
    }

    /**
     * 通过包内资源操作 seam 构造工厂，隔离真实 AdminClient 网络创建与关闭。
     *
     * @param properties Kafka route 配置
     * @param operations 可记录的资源操作
     * @return 待测资源工厂
     */
    private KafkaRouteAdminClientFactory factory(SimpleKafkaRouteProperties properties,
                                                 RecordingOperations operations) {
        SimpleKafkaRouteRegistry registry = new SimpleKafkaRouteRegistry(properties,
                new DefaultKafkaRoutePropertiesValidator(new KafkaRoutePatternMatcher()),
                new MockKafkaProducerFactoryFactory(), new MockKafkaConsumerFactoryFactory());
        try {
            Class<?> operationsType = Class.forName(
                    "io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientOperations");
            InvocationHandler handler = (proxy, method, arguments) -> {
                if ("create".equals(method.getName())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = (Map<String, Object>) arguments[0];
                    return operations.create(config);
                }
                if ("close".equals(method.getName())) {
                    operations.close((AdminClient) arguments[0]);
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            };
            Object operationsProxy = Proxy.newProxyInstance(operationsType.getClassLoader(),
                    new Class<?>[]{operationsType}, handler);
            Constructor<DefaultKafkaRouteAdminClientFactory> constructor =
                    DefaultKafkaRouteAdminClientFactory.class.getDeclaredConstructor(
                            SimpleKafkaRouteProperties.class, SimpleKafkaRouteRegistry.class, operationsType);
            constructor.setAccessible(true);
            return constructor.newInstance(properties, registry, operationsProxy);
        } catch (Exception e) {
            throw new IllegalStateException("创建 AdminClientFactory 测试对象失败", e);
        }
    }

    /**
     * 记录客户端创建、关闭与配置副本，避免单测依赖 broker。
     */
    private static class RecordingOperations {

        private final AdminClient adminClient = Mockito.mock(AdminClient.class);
        private final AtomicInteger createCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final AtomicReference<Map<String, Object>> receivedProperties = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> lastProperties = new AtomicReference<>();
        private RuntimeException createFailure;
        private RuntimeException closeFailure;
        private Error closeError;

        /**
         * 记录每次创建收到的独立配置副本。
         *
         * @param properties 创建配置
         * @return 测试 AdminClient
         */
        private AdminClient create(Map<String, Object> properties) {
            createCount.incrementAndGet();
            receivedProperties.set(properties);
            lastProperties.set(new LinkedHashMap<>(properties));
            if (createFailure != null) {
                throw createFailure;
            }
            return adminClient;
        }

        /**
         * 记录关闭调用，并可模拟 RuntimeException 或 Error。
         *
         * @param ignored 当前关闭的测试 AdminClient
         */
        private void close(AdminClient ignored) {
            closeCount.incrementAndGet();
            if (closeFailure != null) {
                throw closeFailure;
            }
            if (closeError != null) {
                throw closeError;
            }
        }
    }
}
