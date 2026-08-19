package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Kafka topic 分页响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaTopicListResponse {

    /**
     * topic 安全摘要。
     */
    private final List<KafkaTopicResponse> items;
    private final Integer limit;
    private final Integer returned;
    private final Boolean truncated;
    private final Boolean traversalComplete;
    private final String stopReason;
}
