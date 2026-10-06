package io.github.surezzzzzz.sdk.messaging.kafka.outbox.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Outbox 事件脱敏上下文
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class OutboxEventContext {
    /**
     * 记录主键
     */
    private final Long recordId;
    /**
     * 消息 ID
     */
    private final String messageId;
    /**
     * 状态代码
     */
    private final String status;
    /**
     * 已尝试投递总次数
     */
    private final Integer attempt;
    /**
     * 快照协议版本
     */
    private final Integer schemaVersion;
    /**
     * 目标 topic（脱敏后）
     */
    private final String topic;
    /**
     * 显式 datasource（脱敏后）
     */
    private final String datasourceKey;
    /**
     * 最近一次失败的错误码
     */
    private final String errorCode;
    /**
     * broker 返回的实际 topic（脱敏后）
     */
    private final String brokerTopic;
    /**
     * broker 返回的分区
     */
    private final Integer brokerPartition;
    /**
     * broker 返回的 offset
     */
    private final Long brokerOffset;
    /**
     * broker 返回的时间戳（ms）
     */
    private final Long brokerTimestamp;
}
