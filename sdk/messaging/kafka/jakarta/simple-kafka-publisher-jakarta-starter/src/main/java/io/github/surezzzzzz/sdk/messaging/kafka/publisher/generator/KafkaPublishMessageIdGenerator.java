package io.github.surezzzzzz.sdk.messaging.kafka.publisher.generator;

/**
 * Kafka 发布 messageId 生成器
 *
 * @author surezzzzzz
 */
public interface KafkaPublishMessageIdGenerator {

    /**
     * 生成 messageId
     *
     * @return messageId
     */
    String generateMessageId();
}
