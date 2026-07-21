package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support.KafkaOutboxEndToEndHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 租约恢复与终态隔离端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false")
public class KafkaOutboxFaultRecoveryEndToEndTest extends KafkaOutboxEndToEndTestSupport {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";
    private static final long STATE_TIMEOUT_MS = 45000L;
    private static final long POLL_INTERVAL_MS = 200L;

    @Autowired
    private KafkaOutboxEngine outboxEngine;

    @Autowired
    @Qualifier("kafkaOutboxWorker")
    private SmartLifecycle kafkaOutboxWorkerLifecycle;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void recreateOutboxTable() throws IOException {
        kafkaOutboxWorkerLifecycle.stop();
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, OUTBOX_TABLE);
        assertEquals(Integer.valueOf(1), tableCount, "真实 MySQL 中应存在唯一 Outbox 表");
    }

    @AfterEach
    public void ensureWorkerRunning() {
        if (!kafkaOutboxWorkerLifecycle.isRunning()) {
            kafkaOutboxWorkerLifecycle.start();
        }
    }

    @Test
    public void testExpiredLeaseIsReclaimedAndPublishedWithNewOwner() {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-lease-" + suffix;
        String messageId = "mock-message-lease-" + suffix;
        createSharedTopic(topic);

        OutboxSaveResult saved = save(message(topic, key, messageId));
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET status = ?, owner_token = ?,"
                        + " lease_until = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND),"
                        + " available_at = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE id = ?",
                OutboxStatus.PROCESSING.getCode(), "mock-dead-owner", saved.getOutboxRecordId());

        kafkaOutboxWorkerLifecycle.start();
        awaitStatus(messageId, OutboxStatus.SENT.getCode());

        Integer attempt = jdbcTemplate.queryForObject("SELECT attempt FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Integer.class, messageId);
        String ownerToken = jdbcTemplate.queryForObject("SELECT owner_token FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                String.class, messageId);
        Long offset = jdbcTemplate.queryForObject("SELECT broker_offset FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Long.class, messageId);
        java.util.List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        log.info("过期租约恢复结果，messageId={}, attempt={}, ownerToken={}, offset={}, consumedRecordCount={}",
                messageId, attempt, ownerToken, offset, records.size());
        assertEquals(Integer.valueOf(1), attempt, "过期 PROCESSING 记录应被新 Worker 领取一次");
        assertTrue(ownerToken == null, "成功回写后不得遗留旧或新 owner token");
        assertNotNull(offset, "恢复投递后必须回填 broker offset");
        assertEquals(1, records.size(), "恢复领取后必须且只能实际投递一条 Kafka record");
        assertEquals(key, records.get(0).key(), "恢复领取后的 Kafka record key 必须保持稳定");
    }

    @Test
    public void testPoisonSnapshotDoesNotBlockLaterValidRecord() {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String poisonMessageId = "mock-message-poison-" + suffix;
        String validMessageId = "mock-message-valid-" + suffix;
        String validKey = "mock-key-valid-" + suffix;
        createSharedTopic(topic);

        OutboxSaveResult poison = save(message(topic, "mock-key-poison-" + suffix, poisonMessageId));
        OutboxSaveResult valid = save(message(topic, validKey, validMessageId));
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET payload_kind = ?, payload_json = ? WHERE id = ?",
                "JSON", "{", poison.getOutboxRecordId());

        kafkaOutboxWorkerLifecycle.start();
        awaitStatus(poisonMessageId, OutboxStatus.POISON.getCode());
        awaitStatus(validMessageId, OutboxStatus.SENT.getCode());

        Integer poisonAttempt = jdbcTemplate.queryForObject("SELECT attempt FROM " + OUTBOX_TABLE
                + " WHERE message_id = ?", Integer.class, poisonMessageId);
        String poisonErrorCode = jdbcTemplate.queryForObject("SELECT last_error_code FROM " + OUTBOX_TABLE
                + " WHERE message_id = ?", String.class, poisonMessageId);
        Long validOffset = jdbcTemplate.queryForObject("SELECT broker_offset FROM " + OUTBOX_TABLE
                + " WHERE message_id = ?", Long.class, validMessageId);
        java.util.List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(validKey), 1);
        log.info("毒消息隔离结果，poisonMessageId={}, poisonAttempt={}, poisonErrorCode={}, validOffset={}, consumedRecordCount={}",
                poisonMessageId, poisonAttempt, poisonErrorCode, validOffset, records.size());
        assertEquals(Integer.valueOf(1), poisonAttempt, "快照确定性错误必须首次领取后直接终止，不进入无效重试");
        assertNotNull(poisonErrorCode, "毒消息必须记录稳定错误码以支持人工排查");
        assertTrue(poisonErrorCode.startsWith("KAFKA_OUTBOX_"), "毒消息错误码必须属于 Outbox 错误体系");
        assertNotNull(validOffset, "相邻毒消息不得阻塞后续有效消息的 broker 回写");
        assertEquals(1, records.size(), "有效消息必须在毒消息存在时独立且精确投递一条");
        assertEquals(validKey, records.get(0).key(), "有效消息投递 key 必须保持稳定");
    }

    private OutboxSaveResult save(KafkaPublishMessage<String> message) {
        OutboxSaveResult result = transactionTemplate.execute(status -> outboxEngine.save(message));
        assertNotNull(result, "事务内保存应返回 Outbox 结果");
        assertNotNull(result.getOutboxRecordId(), "保存结果应包含真实记录主键");
        return result;
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType("mock.fault.recovery")
                .payload("mock-payload-" + key)
                .build();
    }

    private void awaitStatus(String messageId, String expectedStatus) {
        long deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            status = jdbcTemplate.queryForObject("SELECT status FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                    String.class, messageId);
            if (expectedStatus.equals(status)) {
                return;
            }
            sleep();
        }
        assertEquals(expectedStatus, status, "Outbox 状态必须在超时前到达预期终态");
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Outbox 状态时线程被中断", e);
        }
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "docs", "01_schema.sql");
    }
}
