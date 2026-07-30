package io.github.surezzzzzz.sdk.messaging.kafka.consumer.model;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ConsumerEventType;
import lombok.Builder;
import lombok.Getter;

/**
 * 消费事件上下文，供 {@code KafkaConsumerEventListener} 回调使用
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaConsumerEventContext {

    /**
     * 事件类型
     */
    private final ConsumerEventType eventType;

    /**
     * 消息 id
     */
    private final String messageId;

    /**
     * 消费 topic
     */
    private final String topic;

    /**
     * 分区编号
     */
    private final int partition;

    /**
     * 消费偏移量
     */
    private final long offset;

    /**
     * 尝试次数
     */
    private final int attempt;

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 错误摘要（已脱敏，最长 512）
     */
    private final String errorSummary;

    /**
     * 数据源 key
     */
    private final String datasourceKey;

    /**
     * 消费注册项标识
     */
    private final String registrationId;
}
