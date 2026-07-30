package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

/**
 * 退避策略 SPI，计算重试前的等待时长
 *
 * @author surezzzzzz
 */
public interface KafkaConsumerBackoffPolicy {

    /**
     * 计算第 attempt 次失败后的退避等待时长（毫秒）
     *
     * @param attempt 当前尝试次数（1-based）
     * @return 退避毫秒数
     */
    long computeBackoffMs(int attempt);
}
