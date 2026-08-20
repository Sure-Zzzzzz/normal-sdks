package io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag;

import lombok.Builder;
import lombok.Getter;

/**
 * Kafka 单分区消费积压安全响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerGroupLagResponse {

    private final String topic;
    private final int partition;
    private final long committedOffset;
    private final long endOffset;
    private final long lag;
}
