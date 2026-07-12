package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.factory.DefaultKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka ConsumerFactory 工厂测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerFactoryFactoryTest {

    private final DefaultKafkaConsumerFactoryFactory factoryFactory = new DefaultKafkaConsumerFactoryFactory();

    @Test
    public void testTypedFieldsAndRawPropertiesMappedToKafkaConfig() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getSecurity().setSecurityProtocol("ssl");
        config.getConsumer().setGroupId("mock-group");
        config.getConsumer().setAutoOffsetReset("EARLIEST");
        config.getConsumer().setEnableAutoCommit(false);
        config.getConsumer().setMaxPollRecords(100);
        config.getConsumer().getProperties().put("fetch.min.bytes", "1");

        DefaultKafkaConsumerFactory<Object, Object> factory =
                (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", config);
        Map<String, Object> properties = factory.getConfigurationProperties();

        assertEquals(config.getBootstrapServers(), properties.get(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS));
        assertEquals("mock-client-consumer", properties.get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID));
        assertEquals(SimpleKafkaRouteConstant.DEFAULT_KEY_DESERIALIZER,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_KEY_DESERIALIZER));
        assertEquals(SimpleKafkaRouteConstant.DEFAULT_VALUE_DESERIALIZER,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_VALUE_DESERIALIZER));
        assertEquals("mock-group", properties.get(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));
        assertEquals("earliest", properties.get(SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET));
        assertEquals(false, properties.get(SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT));
        assertEquals(100, properties.get(SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS));
        assertEquals("1", properties.get("fetch.min.bytes"));
        assertEquals("SSL", properties.get(SimpleKafkaRouteConstant.PROPERTY_SECURITY_PROTOCOL));
    }

    @Test
    public void testGroupIdCanBeEmpty() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getConsumer().setGroupId(null);

        DefaultKafkaConsumerFactory<Object, Object> factory =
                (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", config);

        assertFalse(factory.getConfigurationProperties().containsKey(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));
    }

    @Test
    public void testReservedPropertyRejected() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getConsumer().getProperties().put(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID, "mock-group");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config));
        assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
    }

    @Test
    public void testReservedPropertyRejectedIgnoringCaseAtFactoryLayer() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getConsumer().getProperties().put("GROUP.ID", "mock-group");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config));
        assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("GROUP.ID"));
        assertFalse(exception.getMessage().contains("mock-group"));
    }

    @Test
    public void testDeserializerClassMustImplementKafkaDeserializer() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getConsumer().setValueDeserializer(String.class.getName());

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config));
        assertEquals(ErrorCode.KAFKA_ROUTE_011, exception.getErrorCode());
    }
}
