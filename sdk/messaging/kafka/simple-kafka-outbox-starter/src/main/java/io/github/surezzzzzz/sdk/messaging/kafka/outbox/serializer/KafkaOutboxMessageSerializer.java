package io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;

import java.util.Map;

/**
 * Kafka Outbox 消息快照序列化 SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxMessageSerializer {

    /**
     * 序列化 payload
     *
     * @param payload payload
     * @return 快照文本
     */
    String serializePayload(Object payload);

    /**
     * 序列化字符串 Map
     *
     * @param value Map
     * @return JSON 文本
     */
    String serializeStringMap(Map<String, String> value);

    /**
     * 序列化对象 Map
     *
     * @param value Map
     * @return JSON 文本
     */
    String serializeObjectMap(Map<String, Object> value);

    /**
     * 从记录重建发布消息
     *
     * @param record Outbox 记录
     * @return 发布消息
     */
    KafkaPublishMessage<Object> deserialize(OutboxRecordEntity record);
}
