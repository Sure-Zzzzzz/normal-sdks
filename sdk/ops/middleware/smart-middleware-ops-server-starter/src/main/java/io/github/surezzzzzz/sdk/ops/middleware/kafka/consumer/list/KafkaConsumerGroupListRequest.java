package io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.list;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Kafka 消费组清单请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerGroupListRequest implements MiddlewareOpsRequest {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 字面量名称前缀。
     */
    private final String prefix;
    /**
     * 结果数量。
     */
    private final int size;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.KAFKA_CONSUMER_GROUP_LIST;
    }

    @Override
    public String getResourceScope() {
        return "consumer-group-list";
    }
}
