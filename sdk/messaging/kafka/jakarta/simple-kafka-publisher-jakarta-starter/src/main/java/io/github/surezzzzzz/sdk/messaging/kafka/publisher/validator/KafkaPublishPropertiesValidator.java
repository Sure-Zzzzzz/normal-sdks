package io.github.surezzzzzz.sdk.messaging.kafka.publisher.validator;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;

/**
 * Kafka Publisher 配置校验器
 *
 * @author surezzzzzz
 */
public interface KafkaPublishPropertiesValidator {

    /**
     * 校验 Publisher 配置
     *
     * @param properties Publisher 配置
     */
    void validate(SimpleKafkaPublisherProperties properties);
}
