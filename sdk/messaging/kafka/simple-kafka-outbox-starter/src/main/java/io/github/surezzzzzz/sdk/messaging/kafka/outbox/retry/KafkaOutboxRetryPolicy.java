package io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRetryContext;

/**
 * Kafka Outbox 重试策略 SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxRetryPolicy {
    /**
     * 判断失败是否可重试
     *
     * @param context 脱敏重试上下文
     * @param cause   失败原因
     * @return 是否可重试
     */
    boolean isRetryable(OutboxRetryContext context, Throwable cause);

    /**
     * 计算下一次尝试的延迟
     *
     * @param context 脱敏重试上下文，attempt 已累加
     * @return 延迟毫秒数
     */
    long nextDelayMs(OutboxRetryContext context);
}
