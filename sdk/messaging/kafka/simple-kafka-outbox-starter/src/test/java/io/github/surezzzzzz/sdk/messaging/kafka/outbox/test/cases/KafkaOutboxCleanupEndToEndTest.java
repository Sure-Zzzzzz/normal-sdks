package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.KafkaOutboxCleanup;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupBatchResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Kafka Outbox 清理端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.enable=false",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false"
        })
public class KafkaOutboxCleanupEndToEndTest {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";
    private static final int RETENTION_DAYS = 7;
    private static final int CLEANUP_BATCH_SIZE = 2;

    @Autowired
    private KafkaOutboxEngine outboxEngine;

    @Autowired
    private KafkaOutboxRepository repository;

    @Autowired
    private SimpleKafkaOutboxProperties properties;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void recreateOutboxTable() throws IOException {
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
        properties.getCleanup().setRetentionDays(RETENTION_DAYS);
        properties.getCleanup().setBatchSize(CLEANUP_BATCH_SIZE);
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, OUTBOX_TABLE);
        assertEquals(Integer.valueOf(1), tableCount, "真实 MySQL 中应存在唯一 Outbox 表");
    }

    @Test
    public void testCleanupDeletesOnlyExpiredSentRecordsAcrossKeysetBatches() {
        List<String> expiredMessageIds = Arrays.asList(
                save("mock-cleanup-expired-1"), save("mock-cleanup-expired-2"), save("mock-cleanup-expired-3"),
                save("mock-cleanup-expired-4"), save("mock-cleanup-expired-5"));
        String currentSentMessageId = save("mock-cleanup-current-sent");
        String pendingMessageId = save("mock-cleanup-pending");
        String processingMessageId = save("mock-cleanup-processing");
        String retryMessageId = save("mock-cleanup-retry");
        String poisonMessageId = save("mock-cleanup-poison");
        markExpiredSent(expiredMessageIds);
        markCurrentSent(currentSentMessageId);
        markStatus(processingMessageId, OutboxStatus.PROCESSING.getCode());
        markStatus(retryMessageId, OutboxStatus.RETRY_WAIT.getCode());
        markStatus(poisonMessageId, OutboxStatus.POISON.getCode());

        KafkaOutboxCleanup cleanup = new KafkaOutboxCleanup(repository, mock(KafkaOutboxEventListener.class), properties,
                mock(TaskScheduler.class));
        cleanup.cleanupOnce();

        log.info("清理结果，expired={}, currentSent={}, pending={}, processing={}, retry={}, poison={}",
                count(expiredMessageIds), exists(currentSentMessageId), exists(pendingMessageId), exists(processingMessageId),
                exists(retryMessageId), exists(poisonMessageId));
        assertEquals(Integer.valueOf(0), count(expiredMessageIds), "超过保留期的 SENT 必须跨多个批次全部删除");
        assertEquals(Integer.valueOf(1), exists(currentSentMessageId), "保留期内 SENT 不得被删除");
        assertEquals(Integer.valueOf(1), exists(pendingMessageId), "PENDING 不得被清理器删除");
        assertEquals(Integer.valueOf(1), exists(processingMessageId), "PROCESSING 不得被清理器删除");
        assertEquals(Integer.valueOf(1), exists(retryMessageId), "RETRY_WAIT 不得被清理器删除");
        assertEquals(Integer.valueOf(1), exists(poisonMessageId), "POISON 默认不得被清理器删除");
    }

    @Test
    public void testKeysetCursorResumesRemainingExpiredSentRecordsWithoutDuplicateDeletion() {
        String first = save("mock-cleanup-cursor-1");
        String second = save("mock-cleanup-cursor-2");
        String third = save("mock-cleanup-cursor-3");
        markExpiredSent(Arrays.asList(first, second, third));
        Timestamp expireBefore = repository.resolveExpireBefore(RETENTION_DAYS);

        OutboxCleanupBatchResult firstBatch = repository.cleanupBatch(expireBefore, null, null, CLEANUP_BATCH_SIZE);
        OutboxCleanupBatchResult secondBatch = repository.cleanupBatch(expireBefore,
                firstBatch.getLastSentAt(), firstBatch.getLastId(), CLEANUP_BATCH_SIZE);
        OutboxCleanupBatchResult emptyBatch = repository.cleanupBatch(expireBefore,
                secondBatch.getLastSentAt(), secondBatch.getLastId(), CLEANUP_BATCH_SIZE);

        log.info("清理游标结果，firstCandidateCount={}, firstDeletedCount={}, secondCandidateCount={}, secondDeletedCount={},"
                        + " emptyCandidateCount={}", firstBatch.getCandidateCount(), firstBatch.getDeletedCount(),
                secondBatch.getCandidateCount(), secondBatch.getDeletedCount(), emptyBatch.getCandidateCount());
        assertEquals(CLEANUP_BATCH_SIZE, firstBatch.getCandidateCount(), "首批应严格受 batch-size 限制");
        assertEquals(CLEANUP_BATCH_SIZE, firstBatch.getDeletedCount(), "首批候选必须在同一事务内全部删除");
        assertNotNull(firstBatch.getLastSentAt(), "首批必须返回 keyset 时间游标");
        assertNotNull(firstBatch.getLastId(), "首批必须返回 keyset 主键游标");
        assertEquals(1, secondBatch.getCandidateCount(), "恢复游标后应只处理剩余记录");
        assertEquals(1, secondBatch.getDeletedCount(), "恢复游标后应删除唯一剩余记录");
        assertEquals(0, emptyBatch.getCandidateCount(), "所有候选删除后再次恢复不得重复扫描或删除");
        assertEquals(Integer.valueOf(0), count(Arrays.asList(first, second, third)), "所有过期 SENT 最终必须被删除");
        assertNull(jdbcTemplate.queryForObject("SELECT MAX(sent_at) FROM " + OUTBOX_TABLE, Timestamp.class),
                "全部候选已删除时不应残留 SENT 时间戳");
    }

    private String save(String suffix) {
        String messageId = "mock-message-" + suffix;
        OutboxSaveResult result = transactionTemplate.execute(status -> outboxEngine.save(KafkaPublishMessage.<String>builder()
                .topic("mock.outbox.cleanup." + suffix)
                .key("mock-key-" + suffix)
                .messageId(messageId)
                .messageType("mock.cleanup")
                .payload("mock-payload-" + suffix)
                .build()));
        assertNotNull(result, "事务内保存清理候选应返回结果");
        return messageId;
    }

    private void markExpiredSent(List<String> messageIds) {
        for (String messageId : messageIds) {
            jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET status = ?, sent_at = DATE_SUB(CURRENT_TIMESTAMP(3),"
                            + " INTERVAL 8 DAY), owner_token = NULL, lease_until = NULL WHERE message_id = ?",
                    OutboxStatus.SENT.getCode(), messageId);
        }
    }

    private void markCurrentSent(String messageId) {
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET status = ?, sent_at = CURRENT_TIMESTAMP(3),"
                        + " owner_token = NULL, lease_until = NULL WHERE message_id = ?",
                OutboxStatus.SENT.getCode(), messageId);
    }

    private void markStatus(String messageId, String status) {
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET status = ?, owner_token = NULL, lease_until = NULL"
                + " WHERE message_id = ?", status, messageId);
    }

    private Integer exists(String messageId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Integer.class, messageId);
    }

    private Integer count(List<String> messageIds) {
        Integer total = 0;
        for (String messageId : messageIds) {
            total += exists(messageId);
        }
        return total;
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "docs", "01_schema.sql");
    }
}
