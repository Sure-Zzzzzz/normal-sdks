package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.factory.DefaultKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka ConsumerFactory 工厂测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ResourceLock("default-locale")
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
        log.info("consumer typed/raw 合并配置：{}", properties);

        assertEquals(config.getBootstrapServers(), properties.get(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS));
        assertEquals("mock-client-consumer", properties.get(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID));
        assertEquals(SimpleKafkaRouteConstant.DEFAULT_KEY_DESERIALIZER,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_KEY_DESERIALIZER));
        assertEquals(SimpleKafkaRouteConstant.DEFAULT_VALUE_DESERIALIZER,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_VALUE_DESERIALIZER));
        assertEquals("mock-group", properties.get(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));
        assertEquals("earliest", properties.get(SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET));
        assertEquals(Boolean.FALSE, properties.get(SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT));
        assertEquals(100, properties.get(SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS));
        assertEquals("1", properties.get("fetch.min.bytes"));
        assertEquals("SSL", properties.get(SimpleKafkaRouteConstant.PROPERTY_SECURITY_PROTOCOL));
    }

    @Test
    public void testOverrideTakesPrecedenceOverDatasourceConsumerConfiguration() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getConsumer().setGroupId("route-group");
        config.getConsumer().setAutoOffsetReset("latest");
        config.getConsumer().setEnableAutoCommit(true);
        config.getConsumer().setMaxPollRecords(100);

        DefaultKafkaConsumerFactory<Object, Object> factory =
                (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", config,
                        KafkaConsumerFactoryOverride.builder()
                                .groupId(" consumer-group ")
                                .autoOffsetReset(" EARLIEST ")
                                .enableAutoCommit(false)
                                .maxPollRecords(200)
                                .build());
        Map<String, Object> properties = factory.getConfigurationProperties();
        log.info("完整 override 合并配置：{}", properties);

        assertEquals("consumer-group", properties.get(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));
        assertEquals(SimpleKafkaRouteConstant.AUTO_OFFSET_RESET_EARLIEST,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET));
        assertEquals(Boolean.FALSE, properties.get(SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT));
        assertEquals(200, properties.get(SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS));
        assertEquals(config.getBootstrapServers(), properties.get(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS));
        assertEquals(SimpleKafkaRouteConstant.DEFAULT_KEY_DESERIALIZER,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_KEY_DESERIALIZER));
    }

    @Test
    public void testPartialOverrideKeepsUnspecifiedDatasourceValues() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getConsumer().setGroupId("route-group");
        config.getConsumer().setAutoOffsetReset("latest");
        config.getConsumer().setEnableAutoCommit(true);
        config.getConsumer().setMaxPollRecords(100);

        DefaultKafkaConsumerFactory<Object, Object> factory =
                (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", config,
                        KafkaConsumerFactoryOverride.builder().enableAutoCommit(false).build());
        Map<String, Object> properties = factory.getConfigurationProperties();
        log.info("部分 override 合并配置：{}", properties);

        assertEquals("route-group", properties.get(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));
        assertEquals(SimpleKafkaRouteConstant.AUTO_OFFSET_RESET_LATEST,
                properties.get(SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET));
        assertEquals(Boolean.FALSE, properties.get(SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT));
        assertEquals(100, properties.get(SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS));
    }

    @Test
    public void testNullOverrideCreatesIndependentFactory() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getConsumer().setGroupId("route-group");

        DefaultKafkaConsumerFactory<Object, Object> first =
                (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", config, null);
        DefaultKafkaConsumerFactory<Object, Object> second =
                (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", config, null);
        log.info("null override 派生 factory：first={}，second={}", first, second);

        assertNotSame(first, second, "每次派生调用都必须返回独立 factory");
        assertEquals("route-group", first.getConfigurationProperties()
                .get(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));
        assertEquals("route-group", second.getConfigurationProperties()
                .get(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));
    }

    @Test
    public void testInvalidOverrideFailsFast() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");

        assertInvalidOverride(config, KafkaConsumerFactoryOverride.builder().groupId("").build(),
                SimpleKafkaRouteConstant.PROPERTY_GROUP_ID);
        assertInvalidOverride(config, KafkaConsumerFactoryOverride.builder().groupId(" ").build(),
                SimpleKafkaRouteConstant.PROPERTY_GROUP_ID);
        assertInvalidOverride(config, KafkaConsumerFactoryOverride.builder().autoOffsetReset("").build(),
                SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET);
        assertInvalidOverride(config, KafkaConsumerFactoryOverride.builder().autoOffsetReset(" ").build(),
                SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET);
        assertInvalidOverride(config, KafkaConsumerFactoryOverride.builder().autoOffsetReset("invalid").build(),
                SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET);
        assertInvalidOverride(config, KafkaConsumerFactoryOverride.builder().maxPollRecords(0).build(),
                SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS);
        assertInvalidOverride(config, KafkaConsumerFactoryOverride.builder().maxPollRecords(-1).build(),
                SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS);
    }

    @Test
    public void testLocaleRootAppliesToOverrideAndControlledKey() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
            config.getConsumer().setEnableAutoCommit(true);
            DefaultKafkaConsumerFactory<Object, Object> factory =
                    (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", config,
                            KafkaConsumerFactoryOverride.builder()
                                    .autoOffsetReset(" EARLIEST ")
                                    .enableAutoCommit(false)
                                    .build());
            Map<String, Object> properties = factory.getConfigurationProperties();
            log.info("土耳其 Locale 下 override 配置：{}", properties);

            assertEquals(SimpleKafkaRouteConstant.AUTO_OFFSET_RESET_EARLIEST,
                    properties.get(SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET));
            assertEquals(Boolean.FALSE, properties.get(SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT));

            SimpleKafkaRouteProperties.DataSourceConfig invalidConfig = KafkaRouteTestDataHelper.source("mock-client");
            invalidConfig.getConsumer().getProperties().put("GROUP.ID", "mock-value");
            log.info("土耳其 Locale 下准备拒绝受控 key，key=GROUP.ID");
            ConfigurationException exception = assertThrows(ConfigurationException.class,
                    () -> factoryFactory.create("default", invalidConfig));
            log.info("土耳其 Locale 下受控 key 拒绝结果：errorCode={}，message={}",
                    exception.getErrorCode(), exception.getMessage());
            assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("GROUP.ID"));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    public void testConsumerControlledRawPropertiesRejectedAtConsumerAndDatasourceLevels() {
        String[] controlledKeys = {
                SimpleKafkaRouteConstant.PROPERTY_GROUP_ID,
                SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET,
                SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT,
                SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS
        };
        for (String controlledKey : controlledKeys) {
            assertControlledRawPropertyRejected(controlledKey, false);
            assertControlledRawPropertyRejected(controlledKey, true);
        }
    }

    @Test
    public void testUnsetAndBlankDatasourceGroupIdAreOmitted() {
        SimpleKafkaRouteProperties.DataSourceConfig unsetConfig = KafkaRouteTestDataHelper.source("mock-client");
        unsetConfig.getConsumer().setGroupId(null);
        DefaultKafkaConsumerFactory<Object, Object> unsetFactory =
                (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", unsetConfig);
        log.info("未配置 datasource groupId 的配置：{}", unsetFactory.getConfigurationProperties());
        assertFalse(unsetFactory.getConfigurationProperties().containsKey(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));

        SimpleKafkaRouteProperties.DataSourceConfig blankConfig = KafkaRouteTestDataHelper.source("mock-client");
        blankConfig.getConsumer().setGroupId(" ");
        DefaultKafkaConsumerFactory<Object, Object> blankFactory =
                (DefaultKafkaConsumerFactory<Object, Object>) factoryFactory.create("default", blankConfig);
        log.info("空白 datasource groupId 的配置：{}", blankFactory.getConfigurationProperties());
        assertFalse(blankFactory.getConfigurationProperties().containsKey(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID));
    }

    @Test
    public void testDeserializerClassMustImplementKafkaDeserializer() {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        config.getConsumer().setValueDeserializer(String.class.getName());
        log.info("准备拒绝非 Kafka deserializer 类：{}", String.class.getName());

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config));
        log.info("非 Kafka deserializer 类拒绝结果：errorCode={}，message={}",
                exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.KAFKA_ROUTE_011, exception.getErrorCode());
    }

    private void assertInvalidOverride(SimpleKafkaRouteProperties.DataSourceConfig config,
                                       KafkaConsumerFactoryOverride override,
                                       String propertyKey) {
        log.info("准备拒绝非法 override：property={}，override={}", propertyKey, override);
        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config, override));
        log.info("非法 override 被拒绝：property={}，errorCode={}，message={}",
                propertyKey, exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(propertyKey));
    }

    private void assertControlledRawPropertyRejected(String controlledKey, boolean datasourceLevel) {
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("mock-client");
        String inputKey = controlledKey.toUpperCase(Locale.ROOT);
        String inputValue = "mock-value";
        if (datasourceLevel) {
            config.getProperties().put(inputKey, inputValue);
        } else {
            config.getConsumer().getProperties().put(inputKey, inputValue);
        }
        log.info("准备拒绝 consumer 受控 raw key：level={}，key={}，value={}",
                datasourceLevel ? "datasource" : "consumer", inputKey, inputValue);
        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factoryFactory.create("default", config));
        log.info("consumer 受控 raw key 被拒绝：level={}，key={}，errorCode={}，message={}",
                datasourceLevel ? "datasource" : "consumer", inputKey,
                exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.KAFKA_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(inputKey));
        assertFalse(exception.getMessage().contains(inputValue));
    }
}
