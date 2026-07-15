package io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;

/**
 * Kafka 发布 routeKey 解析器
 *
 * @author surezzzzzz
 */
public interface KafkaPublishRouteKeyResolver {

    /**
     * 解析 routeKey
     *
     * @param message 发布消息
     * @return routeKey
     */
    String resolveRouteKey(KafkaPublishMessage<?> message);
}
