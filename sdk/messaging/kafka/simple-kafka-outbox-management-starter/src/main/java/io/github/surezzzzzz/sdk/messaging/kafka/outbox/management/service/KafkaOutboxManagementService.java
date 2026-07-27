package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.service;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view.OutboxRecordCursorPage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view.OutboxRecordDetailView;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view.OutboxStatusSummaryView;

import java.util.List;

/**
 * Outbox 管理服务。
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxManagementService {
    /**
     * 查询状态总览。
     */
    List<OutboxStatusSummaryView> summaries();

    /**
     * 按标识查询详情。
     */
    OutboxRecordDetailView detail(Long recordId);

    /**
     * 按消息标识定位详情。
     */
    OutboxRecordDetailView detailByMessageId(String messageId);

    /**
     * 浏览状态记录。
     */
    OutboxRecordCursorPage browse(String statusCode, String cursor, Integer size);

    /**
     * 重置 POISON 记录。
     */
    void resetPoison(Long recordId);
}
