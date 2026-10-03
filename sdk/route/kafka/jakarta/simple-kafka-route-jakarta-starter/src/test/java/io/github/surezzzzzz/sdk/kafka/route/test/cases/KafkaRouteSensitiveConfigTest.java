package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.factory.DefaultKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 敏感配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRouteSensitiveConfigTest {

    @Test
    public void testToStringDoesNotExposeSensitiveValues() {
        SimpleKafkaRouteProperties.DataSourceConfig source = KafkaRouteTestDataHelper.source("mock-client");
        source.getProperties().put("mock.password", "mock-password-value");
        source.getSecurity().setSaslJaasConfig("mock-jaas-value");
        source.getSecurity().setSslTrustStorePassword("mock-truststore-password");
        source.getSecurity().setSslKeyStorePassword("mock-keystore-password");
        source.getSecurity().setSslKeyPassword("mock-key-password");
        source.getProducer().getProperties().put("producer.password", "mock-producer-password");
        source.getConsumer().getProperties().put("consumer.password", "mock-consumer-password");

        log.info("敏感配置 toString 验证，source={}, security={}", source, source.getSecurity());

        assertFalse(source.toString().contains("mock-password-value"));
        assertFalse(source.toString().contains("mock-jaas-value"));
        assertFalse(source.getSecurity().toString().contains("mock-jaas-value"));
        assertFalse(source.getSecurity().toString().contains("mock-truststore-password"));
        assertFalse(source.getSecurity().toString().contains("mock-keystore-password"));
        assertFalse(source.getSecurity().toString().contains("mock-key-password"));
        assertFalse(source.getProducer().toString().contains("mock-producer-password"));
        assertFalse(source.getConsumer().toString().contains("mock-consumer-password"));
    }

    @Test
    public void testReservedPropertyExceptionDoesNotExposeSensitiveValue() {
        SimpleKafkaRouteProperties.DataSourceConfig source = KafkaRouteTestDataHelper.source("mock-client");
        source.getProperties().put(SimpleKafkaRouteConstant.PROPERTY_SASL_JAAS_CONFIG, "mock-jaas-value");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> new DefaultKafkaProducerFactoryFactory().create("default", source));

        assertTrue(exception.getMessage().contains(SimpleKafkaRouteConstant.PROPERTY_SASL_JAAS_CONFIG));
        assertFalse(exception.getMessage().contains("mock-jaas-value"));
    }
}
