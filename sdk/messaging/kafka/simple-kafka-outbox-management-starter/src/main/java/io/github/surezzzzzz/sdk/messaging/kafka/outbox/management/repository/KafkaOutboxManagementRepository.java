package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.repository;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.query.OutboxRecordBrowseQuery;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord;

import java.util.List;
import java.util.Map;

/**
 * Outbox 管理数据访问。
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxManagementRepository {
    /**
     * 查询状态数量。
     */
    Map<OutboxStatus, Long> countByStatus();

    /**
     * 按标识查询。
     */
    OutboxRecord findById(Long recordId);

    /**
     * 按消息标识查询。
     */
    OutboxRecord findByMessageId(String messageId);

    /**
     * 按状态浏览。
     */
    List<OutboxRecord> browse(OutboxRecordBrowseQuery query);

    /**
     * 重置一条 POISON 记录。
     */
    int resetPoison(Long recordId);
}
