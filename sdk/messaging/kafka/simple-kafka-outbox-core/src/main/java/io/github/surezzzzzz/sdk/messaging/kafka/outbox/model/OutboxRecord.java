package io.github.surezzzzzz.sdk.messaging.kafka.outbox.model;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 不含消息内容的 Outbox 记录领域视图。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class OutboxRecord {

    /**
     * 记录标识。
     */
    private final Long recordId;
    /**
     * 消息标识。
     */
    private final String messageId;
    /**
     * 目标主题。
     */
    private final String topic;
    /**
     * 消息键。
     */
    private final String recordKey;
    /**
     * 路由键。
     */
    private final String routeKey;
    /**
     * 数据源路由键。
     */
    private final String datasourceKey;
    /**
     * 指定分区。
     */
    private final Integer partition;
    /**
     * 消息时间戳。
     */
    private final Long messageTimestamp;
    /**
     * 消息类型。
     */
    private final String messageType;
    /**
     * 消息内容分类。
     */
    private final OutboxPayloadKind payloadKind;
    /**
     * 是否使用信封封装。
     */
    private final Boolean envelopeEnabled;
    /**
     * 链路标识。
     */
    private final String traceId;
    /**
     * 消息快照版本。
     */
    private final Integer snapshotVersion;
    /**
     * 投递状态。
     */
    private final OutboxStatus status;
    /**
     * 已投递尝试次数。
     */
    private final Integer attempt;
    /**
     * 下次可投递时间。
     */
    private final Instant availableAt;
    /**
     * 租约到期时间。
     */
    private final Instant leaseUntil;
    /**
     * 最近失败错误码。
     */
    private final String lastErrorCode;
    /**
     * 最近失败安全摘要。
     */
    private final String lastErrorSummary;
    /**
     * 消息代理实际主题。
     */
    private final String brokerTopic;
    /**
     * 消息代理分区。
     */
    private final Integer brokerPartition;
    /**
     * 消息代理位点。
     */
    private final Long brokerOffset;
    /**
     * 消息代理时间戳。
     */
    private final Long brokerTimestamp;
    /**
     * 创建时间。
     */
    private final Instant createdAt;
    /**
     * 发送成功时间。
     */
    private final Instant sentAt;
    /**
     * 最近更新时间。
     */
    private final Instant updatedAt;
}
