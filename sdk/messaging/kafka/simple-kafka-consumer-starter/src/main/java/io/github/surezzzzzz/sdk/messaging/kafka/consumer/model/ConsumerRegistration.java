package io.github.surezzzzzz.sdk.messaging.kafka.consumer.model;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandler;
import lombok.Builder;
import lombok.Getter;

/**
 * 消费注册项
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ConsumerRegistration {

    /**
     * 消费 topic（多 topic 注册时拆为多个注册项）
     */
    private final String topic;

    /**
     * 显式 datasource key，空时走 route 规则解析
     */
    private final String datasource;

    /**
     * 覆盖 route datasource 的 group-id
     */
    private final String groupId;

    /**
     * 覆盖 route datasource 的 auto-offset-reset
     */
    private final String autoOffsetReset;

    /**
     * 注册项标识，用于日志与事件关联
     */
    private final String id;

    /**
     * 处理器
     */
    private final KafkaConsumerHandler<?, ?> handler;
}
