package io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Kafka Topic 分区状态响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaTopicRuntimeResponse {

    private final String topic;
    private final List<Partition> partitions;
    /**
     * 分区结果是否因服务端固定上限被截断。
     */
    private final boolean truncated;

    /**
     * Topic 单分区状态。
     */
    @Getter
    @Builder
    public static class Partition {
        private final int partition;
        private final Integer leader;
        private final List<Integer> replicas;
        private final List<Integer> inSyncReplicas;
        private final long earliestOffset;
        private final long latestOffset;
    }
}
