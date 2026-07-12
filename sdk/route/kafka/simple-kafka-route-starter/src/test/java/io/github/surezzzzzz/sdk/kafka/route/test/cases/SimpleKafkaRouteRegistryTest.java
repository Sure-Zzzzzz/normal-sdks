package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import io.github.surezzzzzz.sdk.kafka.route.validator.DefaultKafkaRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 注册表测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaRouteRegistryTest {

    @Test
    public void testRegisterDefaultAndNamedDatasourceWithoutCreatingKafkaClient() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        SimpleKafkaRouteRegistry registry = createRegistry(producerFactoryFactory, consumerFactoryFactory);

        assertTrue(registry.containsDatasource("default"));
        assertTrue(registry.containsDatasource("event"));
        assertFalse(registry.containsDatasource("missing"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("default", "event")), registry.getDatasourceKeys());
        assertSame(registry.getProducerFactory("default"), registry.getProducerFactory());
        assertSame(registry.getKafkaTemplate("default"), registry.getKafkaTemplate());
        assertSame(registry.getConsumerFactory("default"), registry.getConsumerFactory());
        assertEquals(0, producerFactoryFactory.getFactories().get("default").getCreateProducerCount());
        assertEquals(0, consumerFactoryFactory.getFactories().get("default").getCreateConsumerCount());
    }

    @Test
    public void testUnknownDatasourceThrowsRouteException() {
        SimpleKafkaRouteRegistry registry = createRegistry(new MockKafkaProducerFactoryFactory(),
                new MockKafkaConsumerFactoryFactory());

        assertThrows(RouteException.class, () -> registry.getKafkaTemplate("missing"));
        assertThrows(RouteException.class, () -> registry.getProducerFactory(" "));
    }

    @Test
    public void testRollbackWhenConsumerFactoryCreateFailed() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        consumerFactoryFactory.setFailDatasourceKey("event");

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

        assertThrows(ConfigurationException.class, () -> createRegistry(producerFactoryFactory, consumerFactoryFactory));
        assertTrue(producerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(consumerFactoryFactory.getFactories().get("default").isDestroyed());
        assertFalse(producerFactoryFactory.getFactories().containsKey("event"));
        assertFalse(consumerFactoryFactory.getFactories().containsKey("event"));
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
        java.util.Set<String> snapshot = registry.getDatasourceKeys();

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

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> createRegistry(producerFactoryFactory, consumerFactoryFactory));
        log.info("factory 配置异常透传 errorCode={}", exception.getErrorCode());

        assertEquals(ErrorCode.KAFKA_ROUTE_011, exception.getErrorCode());
        assertTrue(producerFactoryFactory.getFactories().get("default").isDestroyed());
        assertTrue(producerFactoryFactory.getFactories().get("event").isDestroyed());
        assertTrue(consumerFactoryFactory.getFactories().get("default").isDestroyed());
        assertFalse(consumerFactoryFactory.getFactories().containsKey("event"));
    }

    private SimpleKafkaRouteRegistry createRegistry(MockKafkaProducerFactoryFactory producerFactoryFactory,
                                                   MockKafkaConsumerFactoryFactory consumerFactoryFactory) {
        return new SimpleKafkaRouteRegistry(KafkaRouteTestDataHelper.properties(),
                new DefaultKafkaRoutePropertiesValidator(new KafkaRoutePatternMatcher()),
                producerFactoryFactory,
                consumerFactoryFactory);
    }
}
