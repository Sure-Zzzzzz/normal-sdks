package io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;

/**
 * 默认 Kafka 发布 routeKey 解析器
 *
 * @author surezzzzzz
 */
public class DefaultKafkaPublishRouteKeyResolver implements KafkaPublishRouteKeyResolver {

    /**
     * 解析 routeKey
     *
     * @param message 发布消息
     * @return routeKey
     */
    @Override
    public String resolveRouteKey(KafkaPublishMessage<?> message) {
        if (message == null) {
            return null;
        }
        return message.getRouteKey();
    }
}
