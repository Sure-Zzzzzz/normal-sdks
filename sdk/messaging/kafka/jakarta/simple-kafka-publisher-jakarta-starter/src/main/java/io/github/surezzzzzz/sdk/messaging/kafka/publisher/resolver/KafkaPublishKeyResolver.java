package io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;

/**
 * Kafka 发布 key 解析器
 *
 * @author surezzzzzz
 */
public interface KafkaPublishKeyResolver {

    /**
     * 解析 key
     *
     * @param message 发布消息
     * @return key
     */
    String resolveKey(KafkaPublishMessage<?> message);
}
