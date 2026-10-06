package io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker;

/**
 * Kafka Outbox Worker SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxWorker {

    /**
     * 触发一次领取扫描
     */
    void scanOnce();
}
