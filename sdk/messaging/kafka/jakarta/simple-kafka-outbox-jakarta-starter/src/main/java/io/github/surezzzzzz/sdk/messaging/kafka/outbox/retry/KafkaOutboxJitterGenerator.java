package io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry;

/**
 * Kafka Outbox 随机抖动生成 SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxJitterGenerator {
    /**
     * 生成单位区间随机值
     *
     * @return 大于等于 0 且小于 1 的值
     */
    double nextDouble();
}
