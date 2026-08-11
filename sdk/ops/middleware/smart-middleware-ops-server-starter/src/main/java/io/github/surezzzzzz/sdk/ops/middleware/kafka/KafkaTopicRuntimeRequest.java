package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Kafka Topic 分区状态请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaTopicRuntimeRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String topic;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.KAFKA_TOPIC_RUNTIME;
    }

    @Override
    public String getResourceScope() {
        return "topic-runtime:" + topic;
    }
}
