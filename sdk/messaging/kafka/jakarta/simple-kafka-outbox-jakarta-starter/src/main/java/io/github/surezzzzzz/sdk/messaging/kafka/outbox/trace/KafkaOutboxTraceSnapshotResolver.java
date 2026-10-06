package io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace;

/**
 * Kafka Outbox traceId 快照解析 SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxTraceSnapshotResolver {

    /**
     * 解析当前线程 traceId
     *
     * @return 标准化 traceId
     */
    String resolveTraceId();
}
