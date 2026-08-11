package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Kafka 消费组积压分页响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerGroupLagListResponse {

    private final List<KafkaConsumerGroupLagResponse> items;
}
