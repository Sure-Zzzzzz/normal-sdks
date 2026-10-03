package io.github.surezzzzzz.sdk.kafka.route.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Kafka ConsumerFactory 覆盖配置
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerFactoryOverride {

    /**
     * 消费组标识
     */
    private final String groupId;

    /**
     * 新消费组偏移策略
     */
    private final String autoOffsetReset;

    /**
     * 是否由 Kafka 客户端自动提交 offset
     */
    private final Boolean enableAutoCommit;

    /**
     * 单次 poll 最大记录数
     */
    private final Integer maxPollRecords;
}
