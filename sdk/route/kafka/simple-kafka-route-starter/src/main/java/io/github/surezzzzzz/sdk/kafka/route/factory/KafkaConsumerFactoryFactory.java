package io.github.surezzzzzz.sdk.kafka.route.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Kafka ConsumerFactory 工厂
 *
 * @author surezzzzzz
 */
public interface KafkaConsumerFactoryFactory {

    ConsumerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config);
}
