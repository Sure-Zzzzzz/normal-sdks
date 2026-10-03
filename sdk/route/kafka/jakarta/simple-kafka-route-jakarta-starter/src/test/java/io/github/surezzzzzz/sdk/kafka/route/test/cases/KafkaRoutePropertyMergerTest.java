package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRoutePropertyMerger;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 配置合并 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRoutePropertyMergerTest {

    @Test
    public void testMergeBasePropertiesKeepsOnlyCommonAndSecurityProperties() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getProperties().put("metadata.max.age.ms", "30000");
        config.getSecurity().setSecurityProtocol("sasl_ssl");
        config.getSecurity().setSaslMechanism("PLAIN");
        config.getSecurity().setSaslJaasConfig("${KAFKA_ROUTE_JAAS_CONFIG}");
        config.getSecurity().setSslKeyStorePassword("${KAFKA_ROUTE_SSL_KEY_STORE_PASSWORD}");

        Map<String, Object> merged = KafkaRoutePropertyMerger.mergeBaseProperties("default", config);
        log.info("合并后的 base properties: {}", merged.keySet());

        assertEquals("30000", merged.get("metadata.max.age.ms"));
        assertEquals("SASL_SSL", merged.get(SimpleKafkaRouteConstant.PROPERTY_SECURITY_PROTOCOL));
        assertEquals("PLAIN", merged.get(SimpleKafkaRouteConstant.PROPERTY_SASL_MECHANISM));
        assertEquals("${KAFKA_ROUTE_JAAS_CONFIG}", merged.get(SimpleKafkaRouteConstant.PROPERTY_SASL_JAAS_CONFIG));
        assertEquals("${KAFKA_ROUTE_SSL_KEY_STORE_PASSWORD}",
                merged.get(SimpleKafkaRouteConstant.PROPERTY_SSL_KEYSTORE_PASSWORD));
        assertEquals("mock-client", merged.get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID));
        assertFalse(merged.containsKey(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS),
                "base properties 不应写入 bootstrap.servers，由具体 client factory 最终覆盖");
        assertFalse(merged.containsKey(SimpleKafkaRouteConstant.PROPERTY_KEY_SERIALIZER),
                "base properties 不应写入 serializer，由 producer factory 最终覆盖");
        assertFalse(merged.containsKey(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID),
                "base properties 不应写入 group.id，由 consumer factory 最终覆盖");
    }

    @Test
    public void testReservedPropertyRejectedIgnoringCase() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getProperties().put("BootStrap.Servers", "127.0.0.1:9093");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> KafkaRoutePropertyMerger.mergeBaseProperties("default", config));
        log.info("大小写变体保留 key 被拒绝，errorCode={}", exception.getErrorCode());

        assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("BootStrap.Servers"));
    }

    @Test
    public void testSecurityReservedPropertyRejectedIgnoringCase() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getProducer().getProperties().put("SASL.JAAS.CONFIG", "${KAFKA_ROUTE_JAAS_CONFIG}");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> KafkaRoutePropertyMerger.assertNoReservedKeys("default", config.getProducer().getProperties()));
        log.info("安全保留 key 被拒绝，errorCode={}", exception.getErrorCode());

        assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
        assertFalse(exception.getMessage().contains("KAFKA_ROUTE_JAAS_CONFIG"),
                "异常消息不得输出 raw property value");
    }
}
