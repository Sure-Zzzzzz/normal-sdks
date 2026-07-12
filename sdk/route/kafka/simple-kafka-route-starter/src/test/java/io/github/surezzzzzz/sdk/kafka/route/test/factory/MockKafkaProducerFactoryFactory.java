package io.github.surezzzzzz.sdk.kafka.route.test.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaProducerFactoryFactory;
import org.springframework.kafka.core.ProducerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用 KafkaProducerFactoryFactory
 *
 * @author surezzzzzz
 */
public class MockKafkaProducerFactoryFactory implements KafkaProducerFactoryFactory {

    private final Map<String, RecordingProducerFactory> factories = new LinkedHashMap<>();
    private String failDatasourceKey;

    @Override
    public ProducerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config) {
        if (datasourceKey.equals(failDatasourceKey)) {
            throw new IllegalStateException("mock producer create failed: " + datasourceKey);
        }
        RecordingProducerFactory factory = new RecordingProducerFactory(datasourceKey);
        factories.put(datasourceKey, factory);
        return factory;
    }

    public Map<String, RecordingProducerFactory> getFactories() {
        return factories;
    }

    public void setFailDatasourceKey(String failDatasourceKey) {
        this.failDatasourceKey = failDatasourceKey;
    }
}
