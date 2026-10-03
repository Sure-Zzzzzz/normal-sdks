package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.factory.DefaultKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka ProducerFactory 工厂测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaProducerFactoryFactoryTest {

    private final DefaultKafkaProducerFactoryFactory factoryFactory = new DefaultKafkaProducerFactoryFactory();

    @Test
    public void testTypedFieldsAndRawPropertiesMappedToKafkaConfig() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getSecurity().setSecurityProtocol("sasl_ssl");
        config.getSecurity().setSaslMechanism("PLAIN");
        config.getSecurity().setSaslJaasConfig("${KAFKA_JAAS_CONFIG}");
        config.getProperties().put("metadata.max.age.ms", "30000");
        config.getProducer().setAcks("ALL");
        config.getProducer().setRetries(3);
        config.getProducer().setCompressionType("GZIP");
        config.getProducer().setTransactionIdPrefix("tx-mock-");
        config.getProducer().getProperties().put("partitioner.class", "mock.Partitioner");

        DefaultKafkaProducerFactory<Object, Object> factory =
                (DefaultKafkaProducerFactory<Object, Object>) factoryFactory.create("default", config);
        Map<String, Object> properties = factory.getConfigurationProperties();

        assertEquals(config.getBootstrapServers(), properties.get(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS));
        assertEquals("mock-client-producer", properties.get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID));
        assertEquals(SimpleKafkaRouteConstant.DEFAULT_KEY_SERIALIZER,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_KEY_SERIALIZER));
        assertEquals(SimpleKafkaRouteConstant.DEFAULT_VALUE_SERIALIZER,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_VALUE_SERIALIZER));
        assertEquals("all", properties.get(SimpleKafkaRouteConstant.PROPERTY_ACKS));
        assertEquals("gzip", properties.get(SimpleKafkaRouteConstant.PROPERTY_COMPRESSION_TYPE));
        assertEquals("30000", properties.get("metadata.max.age.ms"));
        assertEquals("mock.Partitioner", properties.get("partitioner.class"));
        assertEquals("SASL_SSL", properties.get(SimpleKafkaRouteConstant.PROPERTY_SECURITY_PROTOCOL));
        assertEquals("${KAFKA_JAAS_CONFIG}", properties.get(SimpleKafkaRouteConstant.PROPERTY_SASL_JAAS_CONFIG));
        assertTrue(new KafkaTemplate<>(factory).isTransactional());
        assertFalse(properties.containsKey(SimpleKafkaRouteConstant.PROPERTY_TRANSACTIONAL_ID));
    }

    @Test
    public void testReservedPropertyRejected() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getProducer().getProperties().put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, "127.0.0.1:9093");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config));
        assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
    }

    @Test
    public void testReservedPropertyRejectedIgnoringCaseAtFactoryLayer() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getProducer().getProperties().put("BootStrap.Servers", "127.0.0.1:9093");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config));
        assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("BootStrap.Servers"));
        assertFalse(exception.getMessage().contains("127.0.0.1:9093"));
    }

    @Test
    public void testSerializerClassMustImplementKafkaSerializer() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getProducer().setKeySerializer(String.class.getName());

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config));
        assertEquals(ErrorCode.KAFKA_ROUTE_011, exception.getErrorCode());
    }
}
