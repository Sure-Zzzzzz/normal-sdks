package io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency;

/**
 * 幂等检查器 SPI。
 *
 * @author surezzzzzz
 */
public interface KafkaConsumerIdempotencyChecker {

    /**
     * 在消费组范围内领取消息处理租约。
     *
     * @param messageId     消息 id
     * @param datasourceKey Kafka datasource
     * @param groupId       Kafka 消费组
     * @return 领取结果
     */
    KafkaConsumerIdempotencyAcquireResult acquire(String messageId, String datasourceKey, String groupId);
}
