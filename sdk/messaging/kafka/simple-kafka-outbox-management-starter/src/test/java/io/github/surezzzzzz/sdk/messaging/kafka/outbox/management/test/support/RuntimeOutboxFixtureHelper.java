package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.support;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;

/**
 * 基于 Runtime 公开 API 创建 Outbox 测试记录。
 *
 * @author surezzzzzz
 */
public class RuntimeOutboxFixtureHelper {
    private static final long LEASE_MICROS = 60_000_000L;
    private static final long RETRY_DELAY_MICROS = 5_000_000L;
    private final KafkaOutboxEngine outboxEngine;
    private final KafkaOutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;

    public RuntimeOutboxFixtureHelper(KafkaOutboxEngine outboxEngine,
                                      KafkaOutboxRepository outboxRepository,
                                      TransactionTemplate transactionTemplate) {
        this.outboxEngine = outboxEngine;
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 创建指定状态的测试记录。
     */
    public long save(String messageId, OutboxStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("样例状态不能为空");
        }
        long recordId = savePending(messageId);
        if (OutboxStatus.PENDING.equals(status)) {
            return recordId;
        }
        OutboxRecordEntity claimed = claim(recordId);
        if (OutboxStatus.PROCESSING.equals(status)) {
            return recordId;
        }
        if (OutboxStatus.RETRY_WAIT.equals(status)) {
            if (!outboxRepository.markRetry(claimed, RETRY_DELAY_MICROS, "MOCK_RETRY", "mock retry summary")) {
                throw new IllegalStateException("Runtime 未将记录迁移到 RETRY_WAIT");
            }
            return recordId;
        }
        if (OutboxStatus.SENT.equals(status)) {
            claimed.setBrokerTopic("mock.broker.topic");
            claimed.setBrokerPartition(1);
            claimed.setBrokerOffset(recordId);
            claimed.setBrokerTimestamp(1_700_000_000_000L + recordId);
            if (!outboxRepository.markSent(claimed)) {
                throw new IllegalStateException("Runtime 未将记录迁移到 SENT");
            }
            return recordId;
        }
        if (OutboxStatus.POISON.equals(status)) {
            if (!outboxRepository.markPoison(claimed, "MOCK_POISON", "mock poison summary")) {
                throw new IllegalStateException("Runtime 未将记录迁移到 POISON");
            }
            return recordId;
        }
        throw new IllegalArgumentException("不支持的样例状态：" + status);
    }

    /**
     * 领取指定记录。
     */
    public OutboxRecordEntity claim(long recordId) {
        List<OutboxRecordEntity> claimed = outboxRepository.claim(1, LEASE_MICROS);
        if (claimed.size() != 1 || !Long.valueOf(recordId).equals(claimed.get(0).getId())) {
            throw new IllegalStateException("Runtime 未领取当前样例记录");
        }
        return claimed.get(0);
    }

    private long savePending(String messageId) {
        OutboxSaveResult result = transactionTemplate.execute(status -> outboxEngine.save(
                KafkaPublishMessage.<String>builder()
                        .topic("mock.topic")
                        .key("mock-record-key")
                        .routeKey("mock-route-key")
                        .datasourceKey("mock-datasource-key")
                        .messageId(messageId)
                        .messageType("mock.type")
                        .payload("mock-payload")
                        .headers(Collections.singletonMap("mock-header", "mock-header-value"))
                        .attributes(Collections.<String, Object>singletonMap("mock-attribute", "mock-attribute-value"))
                        .build()));
        if (result == null || result.getOutboxRecordId() == null) {
            throw new IllegalStateException("Runtime 保存未返回记录 ID");
        }
        return result.getOutboxRecordId();
    }
}
