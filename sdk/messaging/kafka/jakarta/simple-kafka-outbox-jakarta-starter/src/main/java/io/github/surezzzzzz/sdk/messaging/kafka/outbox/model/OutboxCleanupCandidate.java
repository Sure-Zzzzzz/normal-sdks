package io.github.surezzzzzz.sdk.messaging.kafka.outbox.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Timestamp;

/**
 * Outbox 内部清理候选
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public final class OutboxCleanupCandidate {
    /**
     * 清理候选主键
     */
    private final Long recordId;
    /**
     * 候选发送时间，作为 keyset 游标
     */
    private final Timestamp sentAt;
}
