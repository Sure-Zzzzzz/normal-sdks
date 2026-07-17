package io.github.surezzzzzz.sdk.messaging.kafka.publisher.generator;

import java.util.UUID;

/**
 * 默认 Kafka 发布 messageId 生成器
 *
 * @author surezzzzzz
 */
public class DefaultKafkaPublishMessageIdGenerator implements KafkaPublishMessageIdGenerator {

    /**
     * 生成 messageId
     *
     * @return messageId
     */
    @Override
    public String generateMessageId() {
        return UUID.randomUUID().toString();
    }
}
