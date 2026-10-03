package io.github.surezzzzzz.sdk.kafka.route.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka ProducerFactory 工厂
 *
 * @author surezzzzzz
 */
public interface KafkaProducerFactoryFactory {

    ProducerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config);
}
