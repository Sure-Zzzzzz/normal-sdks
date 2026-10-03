package io.github.surezzzzzz.sdk.kafka.route.support;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Kafka route 配置合并 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaRoutePropertyMerger {

    private KafkaRoutePropertyMerger() {
        throw new UnsupportedOperationException(SimpleKafkaRouteConstant.EXCEPTION_MESSAGE_UTILITY_CLASS);
    }

    /**
     * 合并 datasource 基础配置
     *
     * @param datasourceKey datasource key
     * @param config        datasource 配置
     * @return Kafka client 基础配置
     */
    public static Map<String, Object> mergeBaseProperties(String datasourceKey,
                                                          SimpleKafkaRouteProperties.DataSourceConfig config) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (config == null) {
            return properties;
        }
        putRawProperties(datasourceKey, properties, config.getProperties());
        putSecurityProperties(properties, config.getSecurity());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID, config.getClientId());
        return properties;
    }

    /**
     * 校验 raw properties 不包含 route 保留 key
     *
     * @param datasourceKey datasource key
     * @param rawProperties raw properties
     */
    public static void assertNoReservedKeys(String datasourceKey, Map<String, String> rawProperties) {
        if (rawProperties == null || rawProperties.isEmpty()) {
            return;
        }
        for (String key : rawProperties.keySet()) {
            if (isReservedKey(key)) {
                throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_RESERVED_PROPERTY_KEY, datasourceKey, key));
            }
        }
    }

    /**
     * 校验 consumer raw properties 不包含受控 key
     *
     * @param datasourceKey datasource key
     * @param rawProperties raw properties
     */
    public static void assertNoConsumerControlledKeys(String datasourceKey, Map<String, String> rawProperties) {
        if (rawProperties == null || rawProperties.isEmpty()) {
            return;
        }
        for (String key : rawProperties.keySet()) {
            if (isConsumerControlledKey(key)) {
                throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_RESERVED_PROPERTY_KEY, datasourceKey, key));
            }
        }
    }

    /**
     * 校验派生 ConsumerFactory 覆盖配置
     *
     * @param datasourceKey datasource key
     * @param override      覆盖配置
     */
    public static void assertValidConsumerFactoryOverride(String datasourceKey,
                                                          KafkaConsumerFactoryOverride override) {
        if (override == null) {
            return;
        }
        if (override.getGroupId() != null && !KafkaRouteStringHelper.hasText(override.getGroupId())) {
            throw invalidConsumerConfiguration(datasourceKey, SimpleKafkaRouteConstant.PROPERTY_GROUP_ID);
        }
        if (override.getAutoOffsetReset() != null) {
            normalizeAutoOffsetReset(datasourceKey, override.getAutoOffsetReset());
        }
        if (override.getMaxPollRecords() != null && override.getMaxPollRecords() <= 0) {
            throw invalidConsumerConfiguration(datasourceKey, SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS);
        }
    }

    /**
     * 标准化并校验 auto offset reset
     *
     * @param datasourceKey datasource key
     * @param value         配置值
     * @return 标准化后的配置值
     */
    public static String normalizeAutoOffsetReset(String datasourceKey, String value) {
        if (!KafkaRouteStringHelper.hasText(value)) {
            throw invalidConsumerConfiguration(datasourceKey, SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SimpleKafkaRouteConstant.VALID_AUTO_OFFSET_RESET.contains(normalized)) {
            throw invalidConsumerConfiguration(datasourceKey, SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET);
        }
        return normalized;
    }

    private static void putRawProperties(String datasourceKey, Map<String, Object> target,
                                         Map<String, String> rawProperties) {
        assertNoReservedKeys(datasourceKey, rawProperties);
        if (rawProperties == null || rawProperties.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : rawProperties.entrySet()) {
            if (KafkaRouteStringHelper.hasText(entry.getKey())) {
                target.put(entry.getKey().trim(), entry.getValue());
            }
        }
    }

    private static boolean isReservedKey(String key) {
        if (!KafkaRouteStringHelper.hasText(key)) {
            return false;
        }
        return SimpleKafkaRouteConstant.RESERVED_PROPERTY_KEYS.contains(key.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isConsumerControlledKey(String key) {
        if (!KafkaRouteStringHelper.hasText(key)) {
            return false;
        }
        return SimpleKafkaRouteConstant.CONSUMER_CONTROLLED_PROPERTY_KEYS
                .contains(key.trim().toLowerCase(Locale.ROOT));
    }

    private static ConfigurationException invalidConsumerConfiguration(String datasourceKey, String propertyKey) {
        return new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                String.format(ErrorMessage.CONFIG_CONSUMER_INVALID, datasourceKey, propertyKey));
    }

    private static void putSecurityProperties(Map<String, Object> properties,
                                              SimpleKafkaRouteProperties.SecurityConfig security) {
        if (security == null) {
            return;
        }
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_SECURITY_PROTOCOL,
                upper(security.getSecurityProtocol()));
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_SASL_MECHANISM,
                security.getSaslMechanism());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_SASL_JAAS_CONFIG,
                security.getSaslJaasConfig());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_SSL_TRUSTSTORE_LOCATION,
                security.getSslTrustStoreLocation());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_SSL_TRUSTSTORE_PASSWORD,
                security.getSslTrustStorePassword());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_SSL_KEYSTORE_LOCATION,
                security.getSslKeyStoreLocation());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_SSL_KEYSTORE_PASSWORD,
                security.getSslKeyStorePassword());
        putIfHasText(properties, SimpleKafkaRouteConstant.PROPERTY_SSL_KEY_PASSWORD,
                security.getSslKeyPassword());
    }

    private static void putIfHasText(Map<String, Object> properties, String key, String value) {
        if (KafkaRouteStringHelper.hasText(value)) {
            properties.put(key, value.trim());
        }
    }

    private static String upper(String value) {
        return KafkaRouteStringHelper.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : value;
    }
}
