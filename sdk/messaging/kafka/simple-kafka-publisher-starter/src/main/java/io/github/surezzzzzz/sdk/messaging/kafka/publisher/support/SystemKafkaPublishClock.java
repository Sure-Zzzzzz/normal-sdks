package io.github.surezzzzzz.sdk.messaging.kafka.publisher.support;

/**
 * 系统 Kafka 发布时钟
 *
 * @author surezzzzzz
 */
public class SystemKafkaPublishClock implements KafkaPublishClock {

    /**
     * 获取当前系统毫秒时间戳
     *
     * @return 当前系统毫秒时间戳
     */
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
