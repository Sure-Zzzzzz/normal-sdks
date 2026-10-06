package io.github.surezzzzzz.sdk.messaging.kafka.outbox.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Timestamp;

/**
 * Outbox 单批清理结果
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public final class OutboxCleanupBatchResult {

    /**
     * 删除数量
     */
    private final int deletedCount;

    /**
     * 本批最后一条记录的发送时间
     */
    private final Timestamp lastSentAt;

    /**
     * 本批最后一条记录的主键
     */
    private final Long lastId;

    /**
     * 本批候选数量
     */
    private final int candidateCount;
}
