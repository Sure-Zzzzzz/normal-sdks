package io.github.surezzzzzz.sdk.messaging.kafka.consumer.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 解析后的有效消费配置
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class EffectiveConsumerConfiguration {

    private final String datasourceKey;
    private final String groupId;
    private final String autoOffsetReset;
    private final boolean enableAutoCommit;
    private final int maxPollRecords;
    private final int concurrency;
    private final long shutdownAwaitMs;
}
