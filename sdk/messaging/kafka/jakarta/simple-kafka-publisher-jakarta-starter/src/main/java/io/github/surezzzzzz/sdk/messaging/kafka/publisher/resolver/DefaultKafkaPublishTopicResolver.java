package io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;

/**
 * 默认 Kafka 发布 topic 解析器
 *
 * @author surezzzzzz
 */
public class DefaultKafkaPublishTopicResolver implements KafkaPublishTopicResolver {

    /**
     * 解析 topic
     *
     * @param message 发布消息
     * @return topic
     */
    @Override
    public String resolveTopic(KafkaPublishMessage<?> message) {
        return null;
    }
}
