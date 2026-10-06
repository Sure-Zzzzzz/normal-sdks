package io.github.surezzzzzz.sdk.messaging.kafka.outbox.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Timestamp;

/**
 * Outbox 清理上下文
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public final class OutboxCleanupContext {
    /**
     * 删除数量
     */
    private final int deletedCount;
    /**
     * 本次任务固定过期边界
     */
    private final Timestamp expireBefore;
}
