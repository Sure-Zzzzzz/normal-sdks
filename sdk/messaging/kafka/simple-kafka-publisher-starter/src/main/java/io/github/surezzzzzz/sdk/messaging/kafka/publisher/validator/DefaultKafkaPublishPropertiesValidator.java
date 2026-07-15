package io.github.surezzzzzz.sdk.messaging.kafka.publisher.validator;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishHeaderHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishStringHelper;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 默认 Kafka Publisher 配置校验器
 *
 * @author surezzzzzz
 */
public class DefaultKafkaPublishPropertiesValidator implements KafkaPublishPropertiesValidator {

    /**
     * 校验 Publisher 配置
     *
     * @param properties Publisher 配置
     */
    @Override
    public void validate(SimpleKafkaPublisherProperties properties) {
        if (properties == null) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_PROPERTIES_EMPTY);
        }
        validateNestedConfig(properties);
        validateSendConfig(properties.getSend());
        validateHeaderConfig(properties.getHeaders());
        validateAppName(properties);
    }

    private void validateNestedConfig(SimpleKafkaPublisherProperties properties) {
        if (properties.getEnvelope() == null) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_ENVELOPE_CONFIG_EMPTY);
        }
        if (properties.getHeaders() == null) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_HEADER_CONFIG_EMPTY);
        }
        if (properties.getSend() == null) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_SEND_CONFIG_EMPTY);
        }
    }

    private void validateSendConfig(SimpleKafkaPublisherProperties.SendConfig sendConfig) {
        if (sendConfig.getTimeoutMs() <= SimpleKafkaPublisherConstant.ZERO) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_SEND_TIMEOUT_INVALID);
        }
    }

    private void validateHeaderConfig(SimpleKafkaPublisherProperties.HeaderConfig headerConfig) {
        if (!headerConfig.isEnableDefaultHeaders()) {
            return;
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        validateHeaderName(headerConfig.getMessageIdHeader(), normalizedNames);
        validateHeaderName(headerConfig.getMessageTypeHeader(), normalizedNames);
        validateHeaderName(headerConfig.getTraceIdHeader(), normalizedNames);
        validateHeaderName(headerConfig.getSourceHeader(), normalizedNames);
        validateHeaderName(headerConfig.getPublishedAtHeader(), normalizedNames);
    }

    private void validateHeaderName(String headerName, Set<String> normalizedNames) {
        String normalized = KafkaPublishHeaderHelper.normalizeHeaderName(headerName);
        if (!KafkaPublishStringHelper.hasText(normalized)) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_CONFIG_HEADER_NAME_EMPTY);
        }
        if (KafkaPublishStringHelper.containsControlCharacter(normalized)) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_CONFIG_HEADER_NAME_CONTROL);
        }
        if (!normalizedNames.add(normalized.toLowerCase(Locale.ROOT))) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_CONFIG_HEADER_NAME_DUPLICATE);
        }
    }

    private void validateAppName(SimpleKafkaPublisherProperties properties) {
        if ((properties.getEnvelope().isEnable() || properties.getHeaders().isEnableDefaultHeaders())
                && !KafkaPublishStringHelper.hasText(properties.getAppName())) {
            throw configurationInvalid(SimpleKafkaPublisherConstant.REASON_APP_NAME_EMPTY);
        }
    }

    private KafkaPublishConfigurationException configurationInvalid(String reason) {
        return new KafkaPublishConfigurationException(ErrorCode.KAFKA_PUBLISHER_001,
                String.format(ErrorMessage.CONFIG_INVALID, reason));
    }
}
