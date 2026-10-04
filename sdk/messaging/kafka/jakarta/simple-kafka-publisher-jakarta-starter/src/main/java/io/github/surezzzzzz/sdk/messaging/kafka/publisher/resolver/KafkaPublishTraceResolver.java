package io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver;

/**
 * Kafka 发布 traceId 解析器
 *
 * @author surezzzzzz
 */
public interface KafkaPublishTraceResolver {

    /**
     * 解析 traceId
     *
     * <p>返回 null 或 blank 均表示当前没有 traceId；非 blank 结果由发布引擎统一 trim。
     *
     * @return traceId
     */
    String resolveTraceId();
}
