package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.OverrideAwareConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.RecordingConsumerFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import io.github.surezzzzzz.sdk.kafka.route.validator.DefaultKafkaRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 注册表测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaRouteRegistryTest {

    private static final long TEST_WAIT_SECONDS = 5L;

    @Test
    public void testRegisterDefaultAndNamedDatasourceWithoutCreatingKafkaClient() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        SimpleKafkaRouteRegistry registry = createRegistry(producerFactoryFactory, consumerFactoryFactory);
        log.info("注册表数据源：{}，consumer 基础 SPI 调用次数={}", registry.getDatasourceKeys(),
                consumerFactoryFactory.getBaseCreateCount());

        assertTrue(registry.containsDatasource("default"));
        assertTrue(registry.containsDatasource("event"));
        assertFalse(registry.containsDatasource("missing"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("default", "event")), registry.getDatasourceKeys());
        assertSame(registry.getProducerFactory("default"), registry.getProducerFactory());
        assertSame(registry.getKafkaTemplate("default"), registry.getKafkaTemplate());
        assertSame(registry.getConsumerFactory("default"), registry.getConsumerFactory());
        assertEquals(0, producerFactoryFactory.getFactories().get("default").getCreateProducerCount());
        assertEquals(0, consumerFactoryFactory.getFactories().get("default").getCreateConsumerCount());
        assertEquals(2, consumerFactoryFactory.getBaseCreateCount());
    }

    @Test
    public void testUnknownDatasourceThrowsRouteException() {
        SimpleKafkaRouteRegistry registry = createRegistry(new MockKafkaProducerFactoryFactory(),
                new MockKafkaConsumerFactoryFactory());
        log.info("准备拒绝不存在及空白的数据源标识");

        assertThrows(RouteException.class, () -> registry.getKafkaTemplate("missing"));
        assertThrows(RouteException.class, () -> registry.getProducerFactory(" "));
    }

    @Test
    public void testRollbackWhenConsumerFactoryCreateFailed() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        consumerFactoryFactory.setFailDatasourceKey("event");
        log.info("准备模拟 consumer factory 创建失败，datasource=event");

        assertThrows(ConfigurationException.class, () -> createRegistry(producerFactoryFactory, consumerFactoryFactory));
        assertTrue(producerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(producerFactoryFactory.getFactories().get("event").isDestroyed());
        assertTrue(consumerFactoryFactory.getFactories().get("default").isDestroyed());
        assertFalse(consumerFactoryFactory.getFactories().containsKey("event"));
    }

    @Test
    public void testRollbackWhenProducerFactoryCreateFailed() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        producerFactoryFactory.setFailDatasourceKey("event");
        log.info("准备模拟 producer factory 创建失败，datasource=event");

        assertThrows(ConfigurationException.class, () -> createRegistry(producerFactoryFactory, consumerFactoryFactory));
        assertTrue(producerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(consumerFactoryFactory.getFactories().get("default").isDestroyed());
        assertFalse(producerFactoryFactory.getFactories().containsKey("event"));
        assertFalse(consumerFactoryFactory.getFactories().containsKey("event"));
    }

    @Test
    public void testLegacyConsumerFactorySpiRejectsDerivedFactoryRequestsWithoutFallback() {
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        SimpleKafkaRouteRegistry registry = createRegistry(new MockKafkaProducerFactoryFactory(), consumerFactoryFactory);
        int initializedBaseCreateCount = consumerFactoryFactory.getBaseCreateCount();
        log.info("legacy SPI 初始两参数调用次数={}", initializedBaseCreateCount);

        assertDerivedFactoryUnsupported(registry, null);
        assertDerivedFactoryUnsupported(registry, KafkaConsumerFactoryOverride.builder().groupId("mock-group").build());
        log.info("legacy SPI 派生调用后的两参数调用次数={}", consumerFactoryFactory.getBaseCreateCount());

        assertEquals(initializedBaseCreateCount, consumerFactoryFactory.getBaseCreateCount(),
                "派生 API 不能回落调用 legacy 两参数 SPI");
        assertSame(consumerFactoryFactory.getFactories().get("default"), registry.getConsumerFactory("default"),
                "既有共享 factory getter 必须保持兼容");
    }

    @Test
    public void testDerivedFactoryOwnershipAndDestroyedRegistryBoundary() {
        OverrideAwareConsumerFactoryFactory consumerFactoryFactory = new OverrideAwareConsumerFactoryFactory();
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        SimpleKafkaRouteRegistry registry = createOverrideAwareRegistry(producerFactoryFactory, consumerFactoryFactory);

        RecordingConsumerFactory first = (RecordingConsumerFactory) registry.createConsumerFactory("default", null);
        RecordingConsumerFactory second = (RecordingConsumerFactory) registry.createConsumerFactory("default",
                KafkaConsumerFactoryOverride.builder().groupId("mock-group").build());
        log.info("派生 factory 数量={}，基础 factory 数量={}", consumerFactoryFactory.getDerivedFactories().size(),
                consumerFactoryFactory.getBaseFactories().size());

        assertEquals(2, consumerFactoryFactory.getDerivedFactories().size());
        assertEquals(2, consumerFactoryFactory.getBaseFactories().size());
        assertNotSame(first, second, "派生 factory 不得缓存复用");

        first.destroy();
        log.info("调用方销毁首个派生 factory 后：firstDestroyed={}，secondDestroyed={}，baseDestroyed={}",
                first.isDestroyed(), second.isDestroyed(), consumerFactoryFactory.getBaseFactories().get(0).isDestroyed());
        assertTrue(first.isDestroyed());
        assertFalse(second.isDestroyed());
        assertFalse(consumerFactoryFactory.getBaseFactories().get(0).isDestroyed());

        registry.destroy();
        registry.destroy();
        log.info("registry 销毁后：baseDestroyed={}，secondDestroyed={}，datasourceKeys={}",
                consumerFactoryFactory.getBaseFactories().get(0).isDestroyed(), second.isDestroyed(),
                registry.getDatasourceKeys());

        assertTrue(producerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(consumerFactoryFactory.getBaseFactories().get(0).isDestroyed());
        assertFalse(second.isDestroyed(), "registry 不得销毁调用方持有的派生 factory");
        assertTrue(registry.getDatasourceKeys().isEmpty());

        log.info("准备验证已关闭 registry 拒绝新建派生 factory，datasource=default");
        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> registry.createConsumerFactory("default", null));
        log.info("已关闭 registry 拒绝结果：errorCode={}，message={}",
                exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.KAFKA_ROUTE_016, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("default"));

        second.destroy();
        log.info("调用方销毁剩余派生 factory：secondDestroyed={}", second.isDestroyed());
        assertTrue(second.isDestroyed());
    }

    @Test
    public void testInvalidOverrideAndUnknownDatasourceDoNotCallSpi() {
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        SimpleKafkaRouteRegistry registry = createRegistry(new MockKafkaProducerFactoryFactory(), consumerFactoryFactory);
        int initializedBaseCreateCount = consumerFactoryFactory.getBaseCreateCount();
        log.info("准备验证 registry 在 SPI 前拒绝非法 override 与未知数据源，初始调用次数={}",
                initializedBaseCreateCount);

        ConfigurationException invalidOverride = assertThrows(ConfigurationException.class,
                () -> registry.createConsumerFactory("default",
                        KafkaConsumerFactoryOverride.builder().maxPollRecords(0).build()));
        RouteException unknownDatasource = assertThrows(RouteException.class,
                () -> registry.createConsumerFactory("missing", null));
        log.info("registry 前置校验结果：invalidCode={}，unknownCode={}，两参数调用次数={}",
                invalidOverride.getErrorCode(), unknownDatasource.getErrorCode(), consumerFactoryFactory.getBaseCreateCount());

        assertEquals(ErrorCode.KAFKA_ROUTE_005, invalidOverride.getErrorCode());
        assertEquals(ErrorCode.KAFKA_ROUTE_003, unknownDatasource.getErrorCode());
        assertEquals(initializedBaseCreateCount, consumerFactoryFactory.getBaseCreateCount());
    }

    @Test
    public void testNullDerivedFactoryIsRejected() {
        OverrideAwareConsumerFactoryFactory consumerFactoryFactory = new OverrideAwareConsumerFactoryFactory();
        consumerFactoryFactory.setReturnNullDerivedFactory(true);
        SimpleKafkaRouteRegistry registry = createOverrideAwareRegistry(new MockKafkaProducerFactoryFactory(),
                consumerFactoryFactory);
        log.info("准备验证 SPI 返回 null 派生 factory 被拒绝");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> registry.createConsumerFactory("default", null));
        log.info("SPI 返回 null 派生 factory 的结果：errorCode={}，message={}",
                exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.KAFKA_ROUTE_006, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("default"));
        assertTrue(consumerFactoryFactory.getDerivedFactories().isEmpty());
    }

    @Test
    public void testReentrantDestroyDuringDerivedFactoryCreationRejectsAndDestroysFactory() {
        OverrideAwareConsumerFactoryFactory consumerFactoryFactory = new OverrideAwareConsumerFactoryFactory();
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        SimpleKafkaRouteRegistry registry = createOverrideAwareRegistry(producerFactoryFactory, consumerFactoryFactory);
        consumerFactoryFactory.setDerivedFactoryCreatedCallback(registry::destroy);
        log.info("准备验证 SPI 在派生 factory 创建后重入 destroy 时拒绝交付");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> registry.createConsumerFactory("default", null));
        RecordingConsumerFactory derivedFactory = consumerFactoryFactory.getDerivedFactories().get(0);
        log.info("SPI 重入 destroy 结果：errorCode={}，derivedDestroyed={}，baseDestroyed={}，producerDestroyed={}",
                exception.getErrorCode(), derivedFactory.isDestroyed(),
                consumerFactoryFactory.getBaseFactories().get(0).isDestroyed(),
                producerFactoryFactory.getFactories().get("default").isDestroyed());

        assertEquals(ErrorCode.KAFKA_ROUTE_016, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("default"));
        assertTrue(derivedFactory.isDestroyed(), "关闭期间创建的派生 factory 不得交付给调用方");
        assertTrue(consumerFactoryFactory.getBaseFactories().get(0).isDestroyed());
        assertTrue(producerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(registry.getDatasourceKeys().isEmpty());
    }

    @Test
    public void testCreateAndDestroyAreLinearized() throws Exception {
        OverrideAwareConsumerFactoryFactory consumerFactoryFactory = new OverrideAwareConsumerFactoryFactory();
        CountDownLatch derivedCreateEntered = new CountDownLatch(1);
        CountDownLatch derivedCreateRelease = new CountDownLatch(1);
        consumerFactoryFactory.setDerivedCreateLatches(derivedCreateEntered, derivedCreateRelease);
        SimpleKafkaRouteRegistry registry = createOverrideAwareRegistry(new MockKafkaProducerFactoryFactory(),
                consumerFactoryFactory);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<RecordingConsumerFactory> createFuture = executorService.submit(
                    () -> (RecordingConsumerFactory) registry.createConsumerFactory("default", null));
            assertTrue(derivedCreateEntered.await(TEST_WAIT_SECONDS, TimeUnit.SECONDS),
                    "派生 factory 创建线程未在预期时间内进入 SPI");
            log.info("派生 factory 创建线程已进入 SPI，准备并发调用 registry destroy");

            Future<?> destroyFuture = executorService.submit(() -> registry.destroy());
            log.info("destroy 已发起但必须等待派生 factory 创建完成，destroyDone={}", destroyFuture.isDone());
            assertFalse(destroyFuture.isDone(), "派生 factory 创建期间 destroy 不得越过生命周期锁");

            derivedCreateRelease.countDown();
            RecordingConsumerFactory derivedFactory = createFuture.get(TEST_WAIT_SECONDS, TimeUnit.SECONDS);
            destroyFuture.get(TEST_WAIT_SECONDS, TimeUnit.SECONDS);
            log.info("并发 create/destroy 完成：derivedDestroyed={}，datasourceKeys={}",
                    derivedFactory.isDestroyed(), registry.getDatasourceKeys());

            assertFalse(derivedFactory.isDestroyed(), "已交付派生 factory 不得被 registry 销毁");
            assertTrue(registry.getDatasourceKeys().isEmpty());
            log.info("准备验证 destroy 线性化后拒绝后续派生创建");
            ConfigurationException exception = assertThrows(ConfigurationException.class,
                    () -> registry.createConsumerFactory("default", null));
            log.info("关闭后创建拒绝结果：errorCode={}，message={}",
                    exception.getErrorCode(), exception.getMessage());
            assertEquals(ErrorCode.KAFKA_ROUTE_016, exception.getErrorCode());
            derivedFactory.destroy();
            assertTrue(derivedFactory.isDestroyed());
        } finally {
            derivedCreateRelease.countDown();
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(TEST_WAIT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testDestroyIdempotent() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        SimpleKafkaRouteRegistry registry = createRegistry(producerFactoryFactory, consumerFactoryFactory);

        registry.destroy();
        registry.destroy();
        log.info("registry 重复 destroy 后 datasourceKeys={}", registry.getDatasourceKeys());

        assertTrue(producerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(consumerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(registry.getDatasourceKeys().isEmpty());
    }

    @Test
    public void testDatasourceKeysIsSnapshot() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        SimpleKafkaRouteRegistry registry = createRegistry(producerFactoryFactory, consumerFactoryFactory);
        Set<String> snapshot = registry.getDatasourceKeys();

        registry.destroy();
        log.info("destroy 后 registry datasourceKeys={}，snapshot={}", registry.getDatasourceKeys(), snapshot);

        assertEquals(new LinkedHashSet<>(Arrays.asList("default", "event")), snapshot,
                "getDatasourceKeys 应返回快照，不能暴露内部 live view");
        assertTrue(registry.getDatasourceKeys().isEmpty());
    }

    @Test
    public void testConfigurationExceptionFromFactoryKeepsOriginalErrorCodeAfterRollback() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        consumerFactoryFactory.setFailConfigurationDatasourceKey("event", ErrorCode.KAFKA_ROUTE_011);
        log.info("准备模拟 factory 配置异常透传，datasource=event，errorCode={}", ErrorCode.KAFKA_ROUTE_011);

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> createRegistry(producerFactoryFactory, consumerFactoryFactory));
        log.info("factory 配置异常透传结果：errorCode={}", exception.getErrorCode());

        assertEquals(ErrorCode.KAFKA_ROUTE_011, exception.getErrorCode());
        assertTrue(producerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(producerFactoryFactory.getFactories().get("event").isDestroyed());
        assertTrue(consumerFactoryFactory.getFactories().get("default").isDestroyed());
        assertFalse(consumerFactoryFactory.getFactories().containsKey("event"));
    }

    private void assertDerivedFactoryUnsupported(SimpleKafkaRouteRegistry registry,
                                                 KafkaConsumerFactoryOverride override) {
        log.info("准备验证 legacy SPI 拒绝派生 factory：override={}", override);
        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> registry.createConsumerFactory("default", override));
        log.info("legacy SPI 派生 factory 请求被拒绝：errorCode={}，message={}",
                exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.KAFKA_ROUTE_015, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("default"));
    }

    private SimpleKafkaRouteRegistry createOverrideAwareRegistry(
            MockKafkaProducerFactoryFactory producerFactoryFactory,
            OverrideAwareConsumerFactoryFactory consumerFactoryFactory) {
        return new SimpleKafkaRouteRegistry(KafkaRouteTestDataHelper.properties(),
                new DefaultKafkaRoutePropertiesValidator(new KafkaRoutePatternMatcher()),
                producerFactoryFactory,
                consumerFactoryFactory);
    }

    private SimpleKafkaRouteRegistry createRegistry(MockKafkaProducerFactoryFactory producerFactoryFactory,
                                                    MockKafkaConsumerFactoryFactory consumerFactoryFactory) {
        return new SimpleKafkaRouteRegistry(KafkaRouteTestDataHelper.properties(),
                new DefaultKafkaRoutePropertiesValidator(new KafkaRoutePatternMatcher()),
                producerFactoryFactory,
                consumerFactoryFactory);
    }
}
