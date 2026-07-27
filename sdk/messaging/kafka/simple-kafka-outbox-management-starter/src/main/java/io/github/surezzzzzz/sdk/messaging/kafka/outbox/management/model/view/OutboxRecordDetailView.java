package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Outbox 记录详情视图。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class OutboxRecordDetailView {
    /**
     * 记录标识。
     */
    private final Long recordId;
    /**
     * 已掩码消息标识。
     */
    private final String messageId;
    /**
     * 目标主题。
     */
    private final String topic;
    /**
     * 路由键。
     */
    private final String routeKey;
    /**
     * 数据源路由键。
     */
    private final String datasourceKey;
    /**
     * 消息类型。
     */
    private final String messageType;
    /**
     * 内容分类。
     */
    private final OutboxPayloadKind payloadKind;
    /**
     * 状态。
     */
    private final OutboxStatus status;
    /**
     * 已尝试次数。
     */
    private final Integer attempt;
    /**
     * 下次可投递时间。
     */
    private final Instant availableAt;
    /**
     * 最近错误码。
     */
    private final String lastErrorCode;
    /**
     * 最近错误摘要。
     */
    private final String lastErrorSummary;
    /**
     * broker 主题。
     */
    private final String brokerTopic;
    /**
     * broker 分区。
     */
    private final Integer brokerPartition;
    /**
     * broker 位点。
     */
    private final Long brokerOffset;
    /**
     * broker 时间戳。
     */
    private final Long brokerTimestamp;
    /**
     * 创建时间。
     */
    private final Instant createdAt;
    /**
     * 发送时间。
     */
    private final Instant sentAt;
    /**
     * 更新时间。
     */
    private final Instant updatedAt;
}
