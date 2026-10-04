package io.github.surezzzzzz.sdk.messaging.kafka.publisher.support;

/**
 * Kafka 发布时钟
 *
 * @author surezzzzzz
 */
public interface KafkaPublishClock {

    /**
     * 获取当前毫秒时间戳
     *
     * @return 当前毫秒时间戳
     */
    long currentTimeMillis();
}
