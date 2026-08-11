package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Kafka 消费组积压分页请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerGroupLagListRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String groupId;
    private final int size;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.KAFKA_CONSUMER_GROUP_LAG_LIST;
    }

    @Override
    public String getResourceScope() {
        return "consumer-group-lag:" + groupId;
    }
}
