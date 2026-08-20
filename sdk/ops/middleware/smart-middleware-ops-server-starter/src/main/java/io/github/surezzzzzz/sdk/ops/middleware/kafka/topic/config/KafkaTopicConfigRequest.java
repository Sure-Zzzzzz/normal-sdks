package io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.config;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Kafka 精确 Topic 配置请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaTopicConfigRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String topic;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.KAFKA_TOPIC_CONFIG;
    }

    @Override
    public String getResourceScope() {
        return "topic-config:" + topic;
    }
}
