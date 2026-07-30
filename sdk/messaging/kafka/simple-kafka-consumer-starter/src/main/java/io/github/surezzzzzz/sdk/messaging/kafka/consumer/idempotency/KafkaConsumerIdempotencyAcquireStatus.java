package io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency;

/**
 * 幂等处理租约领取状态。
 *
 * @author surezzzzzz
 */
public enum KafkaConsumerIdempotencyAcquireStatus {

    /**
     * 当前投递已获得处理租约。
     */
    ACQUIRED,

    /**
     * 其他投递仍持有未过期处理租约。
     */
    IN_PROGRESS,

    /**
     * 消息已成功完成处理或死信投递。
     */
    COMPLETED
}
