package io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.list;

import lombok.Builder;
import lombok.Getter;

/**
 * Kafka 消费组安全摘要。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerGroupResponse {

    /**
     * 消费组标识。
     */
    private final String groupId;
    /**
     * 协议类型安全摘要。
     */
    private final String protocolType;
}
