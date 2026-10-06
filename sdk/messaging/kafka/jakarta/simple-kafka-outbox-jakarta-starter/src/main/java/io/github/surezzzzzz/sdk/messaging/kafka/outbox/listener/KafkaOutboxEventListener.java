package io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxEventContext;

/**
 * Kafka Outbox 事件 Listener SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxEventListener {
    /**
     * @param context 已保存事件
     */
    void onSaved(OutboxEventContext context);

    /**
     * @param context 已领取事件
     */
    void onClaimed(OutboxEventContext context);

    /**
     * @param context 已发送事件
     */
    void onSent(OutboxEventContext context);

    /**
     * @param context 等待重试事件
     */
    void onRetry(OutboxEventContext context);

    /**
     * @param context 毒消息事件
     */
    void onPoison(OutboxEventContext context);

    /**
     * @param context 租约丢失事件
     */
    void onLeaseLost(OutboxEventContext context);

    /**
     * @param context 清理事件
     */
    void onCleanup(OutboxCleanupContext context);
}
