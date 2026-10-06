package io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace;

/**
 * Kafka Outbox traceId 临时作用域 SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxTraceScope {

    /**
     * 打开 traceId 临时作用域
     *
     * @param traceId 快照 traceId
     * @return 用于精确恢复上下文的句柄
     */
    Scope open(String traceId);

    /**
     * Trace 作用域句柄
     */
    interface Scope extends AutoCloseable {
        /**
         * 精确恢复打开前的上下文
         */
        @Override
        void close();
    }
}
