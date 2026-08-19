package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Kafka 消费组分页响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerGroupListResponse {

    /**
     * 消费组安全摘要。
     */
    private final List<KafkaConsumerGroupResponse> items;
    private final Integer limit;
    private final Integer returned;
    private final Boolean truncated;
    private final Boolean traversalComplete;
    private final String stopReason;
}
