package io.github.surezzzzzz.sdk.messaging.kafka.consumer.model;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandlerAdapter;
import lombok.Builder;
import lombok.Getter;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.List;

/**
 * 消费容器创建上下文，聚合同 datasource 的一组注册项所需信息
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerContainerContext {

    /**
     * 数据源 key
     */
    private final String datasourceKey;

    /**
     * 消费组 id
     */
    private final String groupId;

    /**
     * 消费 topic 列表
     */
    private final List<String> topics;

    /**
     * auto-offset-reset
     */
    private final String autoOffsetReset;

    /**
     * 是否自动提交 offset
     */
    private final boolean enableAutoCommit;

    /**
     * 单次 poll 最大记录数
     */
    private final int maxPollRecords;

    /**
     * 并发消费者数
     */
    private final int concurrency;

    /**
     * 停机等待 in-flight handler 完成时长（毫秒）
     */
    private final long shutdownAwaitMs;

    /**
     * 消息监听器
     */
    private final KafkaConsumerHandlerAdapter listener;

    /**
     * 已按 datasource 和覆盖配置创建的 ConsumerFactory
     */
    private final ConsumerFactory<Object, Object> consumerFactory;
}
