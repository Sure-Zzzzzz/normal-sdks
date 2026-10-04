package io.github.surezzzzzz.sdk.messaging.kafka.consumer.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 消费容器分组键
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public class ConsumerContainerGroupKey {

    private final String datasourceKey;
    private final String groupId;
    private final String autoOffsetReset;
    private final boolean enableAutoCommit;
    private final int maxPollRecords;
    private final int concurrency;

    /**
     * 构造消费容器分组键
     *
     * @param configuration 有效消费配置
     */
    public ConsumerContainerGroupKey(EffectiveConsumerConfiguration configuration) {
        this.datasourceKey = configuration.getDatasourceKey();
        this.groupId = configuration.getGroupId();
        this.autoOffsetReset = configuration.getAutoOffsetReset();
        this.enableAutoCommit = configuration.isEnableAutoCommit();
        this.maxPollRecords = configuration.getMaxPollRecords();
        this.concurrency = configuration.getConcurrency();
    }
}
