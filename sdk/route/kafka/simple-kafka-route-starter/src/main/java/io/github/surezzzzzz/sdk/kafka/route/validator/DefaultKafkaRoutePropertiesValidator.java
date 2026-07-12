package io.github.surezzzzzz.sdk.kafka.route.validator;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRoutePropertyMerger;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteStringHelper;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * 默认 Kafka route 配置校验器
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultKafkaRoutePropertiesValidator implements KafkaRoutePropertiesValidator {

    private final KafkaRoutePatternMatcher patternMatcher;

    @Override
    public void validate(SimpleKafkaRouteProperties properties) {
        validateSources(properties);
        validateRules(properties);
    }

    private void validateSources(SimpleKafkaRouteProperties properties) {
        if (properties.getSources() == null || properties.getSources().isEmpty()) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_001, ErrorMessage.CONFIG_SOURCES_EMPTY);
        }
        if (!properties.getSources().containsKey(properties.getDefaultSource())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_002,
                    String.format(ErrorMessage.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                            properties.getDefaultSource(), properties.getSources().keySet()));
        }
        for (Map.Entry<String, SimpleKafkaRouteProperties.DataSourceConfig> entry : properties.getSources().entrySet()) {
            validateSource(entry.getKey(), entry.getValue());
        }
    }

    private void validateSource(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config) {
        if (!KafkaRouteStringHelper.hasText(datasourceKey)) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005, ErrorMessage.CONFIG_DATASOURCE_KEY_EMPTY);
        }
        if (config == null) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_BOOTSTRAP_SERVERS_EMPTY, datasourceKey));
        }
        validateBootstrapServers(datasourceKey, config.getBootstrapServers());
        validateReservedProperties(datasourceKey, config.getProperties());
        validateSecurity(datasourceKey, config.getSecurity());
        validateProducer(datasourceKey, config.getProducer());
        validateConsumer(datasourceKey, config.getConsumer());
    }

    private void validateBootstrapServers(String datasourceKey, List<String> servers) {
        if (servers == null || servers.isEmpty()) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_BOOTSTRAP_SERVERS_EMPTY, datasourceKey));
        }
        for (String server : servers) {
            if (!isValidEndpoint(server)) {
                throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_BOOTSTRAP_SERVER_INVALID, datasourceKey, server));
            }
        }
    }

    private boolean isValidEndpoint(String server) {
        if (!KafkaRouteStringHelper.hasText(server)) {
            return false;
        }
        String value = server.trim();
        String host;
        String portText;
        if (value.startsWith("[")) {
            int closeIndex = value.indexOf(']');
            if (closeIndex <= 1 || closeIndex + 2 >= value.length() || value.charAt(closeIndex + 1) != ':') {
                return false;
            }
            host = value.substring(1, closeIndex);
            portText = value.substring(closeIndex + 2);
        } else {
            int colonIndex = value.lastIndexOf(':');
            if (colonIndex <= 0 || colonIndex == value.length() - 1) {
                return false;
            }
            if (value.indexOf(':') != colonIndex) {
                return false;
            }
            host = value.substring(0, colonIndex);
            portText = value.substring(colonIndex + 1);
        }
        if (!KafkaRouteStringHelper.hasText(host)) {
            return false;
        }
        try {
            int port = Integer.parseInt(portText);
            return port >= SimpleKafkaRouteConstant.MIN_PORT && port <= SimpleKafkaRouteConstant.MAX_PORT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void validateSecurity(String datasourceKey, SimpleKafkaRouteProperties.SecurityConfig security) {
        if (security == null) {
            return;
        }
        if (KafkaRouteStringHelper.hasText(security.getSecurityProtocol())) {
            String protocol = security.getSecurityProtocol().trim().toUpperCase();
            if (!SimpleKafkaRouteConstant.VALID_SECURITY_PROTOCOLS.contains(protocol)) {
                throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_012,
                        String.format(ErrorMessage.CONFIG_SECURITY_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_SECURITY_PROTOCOL));
            }
            if (protocol.startsWith("SASL") && !KafkaRouteStringHelper.hasText(security.getSaslMechanism())) {
                throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_012,
                        String.format(ErrorMessage.CONFIG_SECURITY_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_SASL_MECHANISM));
            }
        }
    }

    private void validateProducer(String datasourceKey, SimpleKafkaRouteProperties.ProducerConfig producer) {
        if (producer == null) {
            return;
        }
        if (!KafkaRouteStringHelper.hasText(producer.getKeySerializer())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_011,
                    String.format(ErrorMessage.CONFIG_SERIALIZER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_KEY_SERIALIZER));
        }
        if (!KafkaRouteStringHelper.hasText(producer.getValueSerializer())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_011,
                    String.format(ErrorMessage.CONFIG_SERIALIZER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_VALUE_SERIALIZER));
        }
        if (KafkaRouteStringHelper.hasText(producer.getAcks())
                && !SimpleKafkaRouteConstant.VALID_ACKS.contains(producer.getAcks().trim().toLowerCase())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PRODUCER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_ACKS));
        }
        if (producer.getRetries() != null && producer.getRetries() < 0) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PRODUCER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_RETRIES));
        }
        if (producer.getBatchSize() != null && producer.getBatchSize() < 0) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PRODUCER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_BATCH_SIZE));
        }
        if (producer.getLingerMs() != null && producer.getLingerMs() < 0) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PRODUCER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_LINGER_MS));
        }
        if (producer.getBufferMemory() != null && producer.getBufferMemory() < 0) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PRODUCER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_BUFFER_MEMORY));
        }
        if (producer.getRequestTimeoutMs() != null && producer.getRequestTimeoutMs() <= 0) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PRODUCER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_REQUEST_TIMEOUT_MS));
        }
        if (producer.getDeliveryTimeoutMs() != null && producer.getDeliveryTimeoutMs() <= 0) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PRODUCER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_DELIVERY_TIMEOUT_MS));
        }
        if (KafkaRouteStringHelper.hasText(producer.getCompressionType())
                && !SimpleKafkaRouteConstant.VALID_COMPRESSION_TYPES.contains(producer.getCompressionType().trim().toLowerCase())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PRODUCER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_COMPRESSION_TYPE));
        }
        validateReservedProperties(datasourceKey, producer.getProperties());
    }

    private void validateConsumer(String datasourceKey, SimpleKafkaRouteProperties.ConsumerConfig consumer) {
        if (consumer == null) {
            return;
        }
        if (!KafkaRouteStringHelper.hasText(consumer.getKeyDeserializer())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_011,
                    String.format(ErrorMessage.CONFIG_DESERIALIZER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_KEY_DESERIALIZER));
        }
        if (!KafkaRouteStringHelper.hasText(consumer.getValueDeserializer())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_011,
                    String.format(ErrorMessage.CONFIG_DESERIALIZER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_VALUE_DESERIALIZER));
        }
        if (consumer.getMaxPollRecords() != null && consumer.getMaxPollRecords() <= 0) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_CONSUMER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS));
        }
        if (KafkaRouteStringHelper.hasText(consumer.getAutoOffsetReset())
                && !SimpleKafkaRouteConstant.VALID_AUTO_OFFSET_RESET.contains(consumer.getAutoOffsetReset().trim().toLowerCase())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_CONSUMER_INVALID, datasourceKey, SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET));
        }
        validateReservedProperties(datasourceKey, consumer.getProperties());
    }

    private void validateReservedProperties(String datasourceKey, Map<String, String> properties) {
        KafkaRoutePropertyMerger.assertNoReservedKeys(datasourceKey, properties);
    }

    private void validateRules(SimpleKafkaRouteProperties properties) {
        List<SimpleKafkaRouteProperties.RouteRule> rules = properties.getRules();
        if (rules == null) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            validateRule(i, rules.get(i), properties.getSources());
        }
    }

    private void validateRule(int index, SimpleKafkaRouteProperties.RouteRule rule,
                              Map<String, SimpleKafkaRouteProperties.DataSourceConfig> sources) {
        if (rule == null || !rule.isEnable()) {
            return;
        }
        if (!KafkaRouteStringHelper.hasText(rule.getPattern())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_004,
                    String.format(ErrorMessage.CONFIG_ROUTE_PATTERN_EMPTY, index));
        }
        RouteMatchType type = RouteMatchType.fromCode(rule.getType());
        if (type == null) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_007,
                    String.format(ErrorMessage.CONFIG_ROUTE_TYPE_INVALID,
                            index, rule.getType(), rule.getPattern(), rule.getDatasource(), join(RouteMatchType.getAllCodes())));
        }
        if (!sources.containsKey(rule.getDatasource())) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_004,
                    String.format(ErrorMessage.CONFIG_ROUTE_DATASOURCE_NOT_FOUND,
                            index, rule.getDatasource(), rule.getPattern(), rule.getType(), sources.keySet()));
        }
        if (type == RouteMatchType.REGEX || type == RouteMatchType.WILDCARD) {
            try {
                patternMatcher.compile(type, rule.getPattern());
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_004,
                        String.format(ErrorMessage.CONFIG_ROUTE_REGEX_INVALID,
                                index, rule.getPattern(), rule.getType(), rule.getDatasource()), e);
            }
        }
    }

    private String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }
}
