package io.github.surezzzzzz.sdk.messaging.kafka.outbox.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Outbox 保存结果
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public final class OutboxSaveResult {
    /**
     * Outbox 记录主键
     */
    private final Long outboxRecordId;
    /**
     * 最终消息 ID
     */
    private final String messageId;
}
