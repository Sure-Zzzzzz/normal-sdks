package io.github.surezzzzzz.sdk.kafka.route.validator;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;

/**
 * Kafka route 配置校验器
 *
 * @author surezzzzzz
 */
public interface KafkaRoutePropertiesValidator {

    void validate(SimpleKafkaRouteProperties properties);
}
