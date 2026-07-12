package io.github.surezzzzzz.sdk.kafka.route.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaProducerFactoryCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRoutePropertyMerger;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteStringHelper;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认 Kafka ProducerFactory 工厂
 *
 * @author surezzzzzz
 */
public class DefaultKafkaProducerFactoryFactory implements KafkaProducerFactoryFactory {

    @Override
    public ProducerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config) {
        SimpleKafkaRouteProperties.ProducerConfig producer = producerConfig(config);
        try {
            validateSerializer(datasourceKey, SimpleKafkaRouteConstant.PROPERTY_KEY_SERIALIZER,
                    producer.getKeySerializer());
            validateSerializer(datasourceKey, SimpleKafkaRouteConstant.PROPERTY_VALUE_SERIALIZER,
                    producer.getValueSerializer());
            Map<String, Object> producerProperties = createProducerProperties(datasourceKey, config, producer);
            DefaultKafkaProducerFactory<Object, Object> producerFactory = new DefaultKafkaProducerFactory<>(producerProperties);
            KafkaProducerFactoryCompatibilityHelper.applyTransactionIdPrefix(producerFactory,
                    producer.getTransactionIdPrefix());
            return producerFactory;
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_006,
                    String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey), e);
        }
    }

    private Map<String, Object> createProducerProperties(String datasourceKey,
                                                         SimpleKafkaRouteProperties.DataSourceConfig config,
                                                         SimpleKafkaRouteProperties.ProducerConfig producer) {
        Map<String, Object> properties = new LinkedHashMap<>(
                KafkaRoutePropertyMerger.mergeBaseProperties(datasourceKey, config));
        putProducerTypedProperties(properties, producer);
        putRawProperties(datasourceKey, properties, producer.getProperties());
        putFinalProducerProperties(properties, config, producer);
        return properties;
    }

    private void putProducerTypedProperties(Map<String, Object> properties,
                                            SimpleKafkaRouteProperties.ProducerConfig producer) {
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID, producer.getClientId());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_ACKS, lower(producer.getAcks()));
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_RETRIES, producer.getRetries());
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_BATCH_SIZE, producer.getBatchSize());
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_LINGER_MS, producer.getLingerMs());
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_BUFFER_MEMORY, producer.getBufferMemory());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_COMPRESSION_TYPE, lower(producer.getCompressionType()));
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_ENABLE_IDEMPOTENCE, producer.getEnableIdempotence());
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_REQUEST_TIMEOUT_MS, producer.getRequestTimeoutMs());
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_DELIVERY_TIMEOUT_MS, producer.getDeliveryTimeoutMs());
    }

    private void putFinalProducerProperties(Map<String, Object> properties,
                                            SimpleKafkaRouteProperties.DataSourceConfig config,
                                            SimpleKafkaRouteProperties.ProducerConfig producer) {
        properties.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, config.getBootstrapServers());
        properties.put(SimpleKafkaRouteConstant.PROPERTY_KEY_SERIALIZER, producer.getKeySerializer().trim());
        properties.put(SimpleKafkaRouteConstant.PROPERTY_VALUE_SERIALIZER, producer.getValueSerializer().trim());
        if (KafkaRouteStringHelper.hasText(producer.getClientId())) {
            properties.put(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID, producer.getClientId().trim());
        } else if (KafkaRouteStringHelper.hasText(config.getClientId())) {
            properties.put(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID, config.getClientId().trim());
        }
    }

    private void putRawProperties(String datasourceKey, Map<String, Object> target, Map<String, String> rawProperties) {
        KafkaRoutePropertyMerger.assertNoReservedKeys(datasourceKey, rawProperties);
        if (rawProperties == null || rawProperties.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : rawProperties.entrySet()) {
            if (KafkaRouteStringHelper.hasText(entry.getKey())) {
                target.put(entry.getKey().trim(), entry.getValue());
            }
        }
    }

    private void validateSerializer(String datasourceKey, String field, String className) {
        if (!KafkaRouteStringHelper.hasText(className)) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_011,
                    String.format(ErrorMessage.CONFIG_SERIALIZER_INVALID, datasourceKey, field));
        }
        try {
            Class<?> serializerClass = Class.forName(className.trim());
            if (!Serializer.class.isAssignableFrom(serializerClass)) {
                throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_011,
                        String.format(ErrorMessage.CONFIG_SERIALIZER_INVALID, datasourceKey, field));
            }
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_011,
                    String.format(ErrorMessage.CONFIG_SERIALIZER_INVALID, datasourceKey, field), e);
        }
    }

    private SimpleKafkaRouteProperties.ProducerConfig producerConfig(SimpleKafkaRouteProperties.DataSourceConfig config) {
        return config.getProducer() == null ? new SimpleKafkaRouteProperties.ProducerConfig() : config.getProducer();
    }

    private void putIfHasText(Map<String, Object> properties, String key, String value) {
        if (KafkaRouteStringHelper.hasText(value)) {
            properties.put(key, value.trim());
        }
    }

    private void putIfNotNull(Map<String, Object> properties, String key, Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    private String lower(String value) {
        return KafkaRouteStringHelper.hasText(value) ? value.trim().toLowerCase() : value;
    }
}
