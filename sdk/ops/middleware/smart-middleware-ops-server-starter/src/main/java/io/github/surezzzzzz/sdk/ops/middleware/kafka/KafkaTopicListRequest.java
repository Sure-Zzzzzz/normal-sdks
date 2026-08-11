package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Kafka topic 清单请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaTopicListRequest implements MiddlewareOpsRequest {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 结果数量。
     */
    private final int size;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.KAFKA_TOPIC_LIST;
    }

    @Override
    public String getResourceScope() {
        return "topic-list";
    }
}
