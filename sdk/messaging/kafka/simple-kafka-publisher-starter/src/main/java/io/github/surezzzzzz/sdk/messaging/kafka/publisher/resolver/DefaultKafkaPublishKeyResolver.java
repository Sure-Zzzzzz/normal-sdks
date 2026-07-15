package io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;

/**
 * 默认 Kafka 发布 key 解析器
 *
 * @author surezzzzzz
 */
public class DefaultKafkaPublishKeyResolver implements KafkaPublishKeyResolver {

    /**
     * 解析 key
     *
     * @param message 发布消息
     * @return key
     */
    @Override
    public String resolveKey(KafkaPublishMessage<?> message) {
        if (message == null) {
            return null;
        }
        return message.getKey();
    }
}
