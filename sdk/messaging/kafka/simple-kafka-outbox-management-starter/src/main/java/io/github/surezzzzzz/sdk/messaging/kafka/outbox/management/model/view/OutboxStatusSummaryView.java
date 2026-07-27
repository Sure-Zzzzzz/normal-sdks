package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * Outbox 状态汇总视图。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class OutboxStatusSummaryView {
    /**
     * 状态。
     */
    private final OutboxStatus status;
    /**
     * 数量。
     */
    private final Long count;
}
