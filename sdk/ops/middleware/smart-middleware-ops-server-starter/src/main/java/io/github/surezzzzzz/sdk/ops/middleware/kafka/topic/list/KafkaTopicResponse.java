package io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.list;

import lombok.Builder;
import lombok.Getter;

/**
 * Kafka topic 安全摘要。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaTopicResponse {

    /**
     * topic 名称。
     */
    private final String name;
}
