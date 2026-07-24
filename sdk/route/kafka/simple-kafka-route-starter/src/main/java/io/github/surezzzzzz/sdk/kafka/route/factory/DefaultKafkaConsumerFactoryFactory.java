package io.github.surezzzzzz.sdk.kafka.route.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRoutePropertyMerger;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteStringHelper;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 默认 Kafka ConsumerFactory 工厂
 *
 * @author surezzzzzz
 */
public class DefaultKafkaConsumerFactoryFactory implements KafkaConsumerFactoryFactory {

    @Override
    public ConsumerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config) {
        return create(datasourceKey, config, null);
    }

    @Override
    public ConsumerFactory<Object, Object> create(String datasourceKey,
                                                  SimpleKafkaRouteProperties.DataSourceConfig config,
                                                  KafkaConsumerFactoryOverride override) {
        SimpleKafkaRouteProperties.ConsumerConfig consumer = consumerConfig(config);
        try {
            KafkaRoutePropertyMerger.assertValidConsumerFactoryOverride(datasourceKey, override);
            Deserializer<Object> keyDeserializer = createDeserializer(datasourceKey,
                    SimpleKafkaRouteConstant.PROPERTY_KEY_DESERIALIZER, consumer.getKeyDeserializer());
            Deserializer<Object> valueDeserializer = createDeserializer(datasourceKey,
                    SimpleKafkaRouteConstant.PROPERTY_VALUE_DESERIALIZER, consumer.getValueDeserializer());
            return new DefaultKafkaConsumerFactory<>(
                    createConsumerProperties(datasourceKey, config, consumer, override),
                    keyDeserializer, valueDeserializer);
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_006,
                    String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey), e);
        }
    }

    private Map<String, Object> createConsumerProperties(String datasourceKey,
                                                         SimpleKafkaRouteProperties.DataSourceConfig config,
                                                         SimpleKafkaRouteProperties.ConsumerConfig consumer,
                                                         KafkaConsumerFactoryOverride override) {
        KafkaRoutePropertyMerger.assertNoConsumerControlledKeys(datasourceKey, config.getProperties());
        Map<String, Object> properties = new LinkedHashMap<>(
                KafkaRoutePropertyMerger.mergeBaseProperties(datasourceKey, config));
        putConsumerTypedProperties(properties, consumer);
        putRawProperties(datasourceKey, properties, consumer.getProperties());
        putOverrideProperties(properties, override);
        putFinalConsumerProperties(properties, config, consumer, override);
        return properties;
    }

    private void putConsumerTypedProperties(Map<String, Object> properties,
                                            SimpleKafkaRouteProperties.ConsumerConfig consumer) {
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID, consumer.getClientId());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_GROUP_ID, consumer.getGroupId());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET, lower(consumer.getAutoOffsetReset()));
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT, consumer.getEnableAutoCommit());
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS, consumer.getMaxPollRecords());
    }

    private void putOverrideProperties(Map<String, Object> properties, KafkaConsumerFactoryOverride override) {
        if (override == null) {
            return;
        }
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_GROUP_ID, override.getGroupId());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET,
                lower(override.getAutoOffsetReset()));
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT,
                override.getEnableAutoCommit());
        putIfNotNull(properties, SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS,
                override.getMaxPollRecords());
    }

    private void putFinalConsumerProperties(Map<String, Object> properties,
                                            SimpleKafkaRouteProperties.DataSourceConfig config,
                                            SimpleKafkaRouteProperties.ConsumerConfig consumer,
                                            KafkaConsumerFactoryOverride override) {
        properties.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, config.getBootstrapServers());
        properties.put(SimpleKafkaRouteConstant.PROPERTY_KEY_DESERIALIZER, consumer.getKeyDeserializer().trim());
        properties.put(SimpleKafkaRouteConstant.PROPERTY_VALUE_DESERIALIZER, consumer.getValueDeserializer().trim());
        if (KafkaRouteStringHelper.hasText(consumer.getClientId())) {
            properties.put(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID, consumer.getClientId().trim());
        } else if (KafkaRouteStringHelper.hasText(config.getClientId())) {
            properties.put(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID, config.getClientId().trim());
        }
        if (override != null && KafkaRouteStringHelper.hasText(override.getGroupId())) {
            properties.put(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID, override.getGroupId().trim());
        } else if (KafkaRouteStringHelper.hasText(consumer.getGroupId())) {
            properties.put(SimpleKafkaRouteConstant.PROPERTY_GROUP_ID, consumer.getGroupId().trim());
        }
    }

    private void putRawProperties(String datasourceKey, Map<String, Object> target, Map<String, String> rawProperties) {
        KafkaRoutePropertyMerger.assertNoReservedKeys(datasourceKey, rawProperties);
        KafkaRoutePropertyMerger.assertNoConsumerControlledKeys(datasourceKey, rawProperties);
        if (rawProperties == null || rawProperties.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : rawProperties.entrySet()) {
            if (KafkaRouteStringHelper.hasText(entry.getKey())) {
                target.put(entry.getKey().trim(), entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Deserializer<Object> createDeserializer(String datasourceKey, String field, String className) {
        if (!KafkaRouteStringHelper.hasText(className)) {
            throw deserializerInvalid(datasourceKey, field, null);
        }
        try {
            Class<?> deserializerClass = Class.forName(className.trim());
            if (!Deserializer.class.isAssignableFrom(deserializerClass)) {
                throw deserializerInvalid(datasourceKey, field, null);
            }
            return (Deserializer<Object>) deserializerClass.getDeclaredConstructor().newInstance();
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw deserializerInvalid(datasourceKey, field, causeOf(e));
        }
    }

    private Throwable causeOf(Exception exception) {
        if (exception instanceof InvocationTargetException
                && ((InvocationTargetException) exception).getCause() != null) {
            return ((InvocationTargetException) exception).getCause();
        }
        return exception;
    }

    private ConfigurationException deserializerInvalid(String datasourceKey, String field, Throwable cause) {
        String message = String.format(ErrorMessage.CONFIG_DESERIALIZER_INVALID, datasourceKey, field);
        return cause == null ? new ConfigurationException(ErrorCode.KAFKA_ROUTE_011, message)
                : new ConfigurationException(ErrorCode.KAFKA_ROUTE_011, message, cause);
    }

    private SimpleKafkaRouteProperties.ConsumerConfig consumerConfig(SimpleKafkaRouteProperties.DataSourceConfig config) {
        return config.getConsumer() == null ? new SimpleKafkaRouteProperties.ConsumerConfig() : config.getConsumer();
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
        return KafkaRouteStringHelper.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : value;
    }
}
