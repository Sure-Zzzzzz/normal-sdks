package io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;

/**
 * Kafka 发布 topic 解析器
 *
 * @author surezzzzzz
 */
public interface KafkaPublishTopicResolver {

    /**
     * 解析 topic
     *
     * @param message 发布消息
     * @return topic
     */
    String resolveTopic(KafkaPublishMessage<?> message);
}
