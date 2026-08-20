package io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Kafka 精确消费组详情请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerGroupDetailRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String groupId;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.KAFKA_CONSUMER_GROUP_DETAIL;
    }

    @Override
    public String getResourceScope() {
        return "consumer-group-detail:" + groupId;
    }
}
