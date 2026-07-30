package io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency;

/**
 * 消息处理租约。
 *
 * @author surezzzzzz
 */
public interface KafkaConsumerIdempotencyLease {

    /**
     * 将当前处理租约标记为已完成。
     *
     * @return true 表示当前 owner 成功完成状态转换
     */
    boolean complete();

    /**
     * 释放当前处理租约。
     *
     * @return true 表示当前 owner 成功释放租约
     */
    boolean release();
}
