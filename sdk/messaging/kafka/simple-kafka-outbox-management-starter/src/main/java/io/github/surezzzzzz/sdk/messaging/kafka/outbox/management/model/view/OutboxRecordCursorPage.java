package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Outbox 游标分页视图。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class OutboxRecordCursorPage {
    /**
     * 当前记录。
     */
    private final List<OutboxRecordListItemView> records;
    /**
     * 是否存在下一批。
     */
    private final boolean hasNext;
    /**
     * 下一批游标。
     */
    private final String nextCursor;
}
