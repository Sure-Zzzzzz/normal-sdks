package io.github.surezzzzzz.sdk.kafka.route.test.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaConsumerFactoryFactory;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用 KafkaConsumerFactoryFactory
 *
 * @author surezzzzzz
 */
public class MockKafkaConsumerFactoryFactory implements KafkaConsumerFactoryFactory {

    private final Map<String, RecordingConsumerFactory> factories = new LinkedHashMap<>();
    private String failDatasourceKey;
    private String failConfigurationDatasourceKey;
    private String failConfigurationErrorCode;

    @Override
    public ConsumerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config) {
        if (datasourceKey.equals(failDatasourceKey)) {
            throw new IllegalStateException("mock consumer create failed: " + datasourceKey);
        }
        if (datasourceKey.equals(failConfigurationDatasourceKey)) {
            throw new ConfigurationException(failConfigurationErrorCode,
                    "mock consumer configuration failed: " + datasourceKey);
        }
        RecordingConsumerFactory factory = new RecordingConsumerFactory(datasourceKey);
        factories.put(datasourceKey, factory);
        return factory;
    }

    public Map<String, RecordingConsumerFactory> getFactories() {
        return factories;
    }

    public void setFailDatasourceKey(String failDatasourceKey) {
        this.failDatasourceKey = failDatasourceKey;
    }

    public void setFailConfigurationDatasourceKey(String failConfigurationDatasourceKey,
                                                  String failConfigurationErrorCode) {
        this.failConfigurationDatasourceKey = failConfigurationDatasourceKey;
        this.failConfigurationErrorCode = failConfigurationErrorCode;
    }
}
