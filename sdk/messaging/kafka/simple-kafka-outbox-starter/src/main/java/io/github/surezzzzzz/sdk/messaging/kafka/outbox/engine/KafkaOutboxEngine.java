package io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;

/**
 * Kafka Outbox 核心 SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxEngine {

    /**
     * 在当前活跃事务中保存消息快照
     *
     * @param message 待发布消息
     * @param <T>     payload 类型
     * @return 保存结果
     */
    <T> OutboxSaveResult save(KafkaPublishMessage<T> message);
}
