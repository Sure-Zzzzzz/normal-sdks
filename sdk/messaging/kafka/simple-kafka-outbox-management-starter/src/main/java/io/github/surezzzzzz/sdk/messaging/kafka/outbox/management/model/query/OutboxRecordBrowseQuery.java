package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.query;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Outbox 状态浏览查询。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class OutboxRecordBrowseQuery {
    /**
     * 状态。
     */
    private final OutboxStatus status;
    /**
     * 游标可投递时间。
     */
    private final Instant cursorAvailableAt;
    /**
     * 游标记录标识。
     */
    private final Long cursorId;
    /**
     * 页面条数。
     */
    private final int size;
}
