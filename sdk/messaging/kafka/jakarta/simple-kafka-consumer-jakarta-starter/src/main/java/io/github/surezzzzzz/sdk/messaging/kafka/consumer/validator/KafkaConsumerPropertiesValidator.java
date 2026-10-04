package io.github.surezzzzzz.sdk.messaging.kafka.consumer.validator;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;

/**
 * Kafka Consumer Properties 校验器
 *
 * @author surezzzzzz
 */
public interface KafkaConsumerPropertiesValidator {

    /**
     * 校验配置
     *
     * @param properties 配置
     */
    void validate(SimpleKafkaConsumerProperties properties);
}
