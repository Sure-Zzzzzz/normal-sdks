package io.github.surezzzzzz.sdk.messaging.kafka.publisher.support;

/**
 * Kafka 发布 traceId 解析器
 *
 * @author surezzzzzz
 */
public interface KafkaPublishTraceResolver {

    /**
     * 解析 traceId
     *
     * @return traceId
     */
    String resolveTraceId();
}
