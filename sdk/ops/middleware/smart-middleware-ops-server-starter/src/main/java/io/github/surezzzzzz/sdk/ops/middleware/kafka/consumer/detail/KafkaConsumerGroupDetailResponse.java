package io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Kafka 消费组安全详情响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerGroupDetailResponse {

    private final String groupId;
    private final String state;
    private final String protocolType;
    private final String assignmentStatus;
    private final Integer memberCount;
    private final List<Assignment> assignments;
    private final Boolean truncated;

    @Getter
    @Builder
    public static class Assignment {
        private final String topic;
        private final List<Integer> partitions;
    }
}
