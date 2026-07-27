package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Outbox 列表记录视图。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class OutboxRecordListItemView {
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
     * 安全错误摘要。
     */
    private final String lastErrorSummary;
}
