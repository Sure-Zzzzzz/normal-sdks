package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support.KafkaOutboxEndToEndHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 事务原子性端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false")
public class KafkaOutboxTransactionEndToEndTest extends KafkaOutboxEndToEndTestSupport {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";
    private static final String BUSINESS_TABLE = "mock_outbox_business_record";
    private static final long SENT_TIMEOUT_MS = 45000L;
    private static final long POLL_INTERVAL_MS = 200L;

    @Autowired
    private KafkaOutboxEngine outboxEngine;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void recreateTables() throws IOException {
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + BUSINESS_TABLE);
        jdbcTemplate.execute("CREATE TABLE " + BUSINESS_TABLE
                + " (id BIGINT NOT NULL AUTO_INCREMENT, business_key VARCHAR(191) NOT NULL,"
                + " PRIMARY KEY (id), UNIQUE KEY uk_business_key (business_key)) ENGINE=InnoDB");
        assertEquals(Integer.valueOf(1), tableCount(OUTBOX_TABLE), "真实 MySQL 中应重建 Outbox 表");
        assertEquals(Integer.valueOf(1), tableCount(BUSINESS_TABLE), "真实 MySQL 中应重建业务表");
    }

    @Test
    public void testCommittedBusinessAndOutboxRecordsAreAtomicallyPersistedThenPublished() {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String businessKey = "mock-business-" + suffix;
        String messageId = "mock-message-" + suffix;
        String key = "mock-key-" + suffix;
        createSharedTopic(topic);

        OutboxSaveResult result = transactionTemplate.execute(status -> {
            jdbcTemplate.update("INSERT INTO " + BUSINESS_TABLE + " (business_key) VALUES (?)", businessKey);
            return outboxEngine.save(message(topic, key, messageId));
        });

        assertNotNull(result, "提交事务应返回 Outbox 保存结果");
        assertNotNull(result.getOutboxRecordId(), "Outbox 保存结果应包含真实主键");
        assertEquals(messageId, result.getMessageId(), "保存结果必须保留稳定 messageId");
        assertEquals(Integer.valueOf(1), count(BUSINESS_TABLE, "business_key", businessKey),
                "提交后业务行必须存在");
        assertEquals(Integer.valueOf(1), count(OUTBOX_TABLE, "message_id", messageId),
                "提交后 Outbox 行必须存在");

        awaitSent(messageId);
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        log.info("事务提交端到端结果，businessKey={}, messageId={}, consumedRecordCount={}",
                businessKey, messageId, records.size());
        assertEquals(1, records.size(), "提交后的 Outbox 消息必须且只能被默认 Kafka 集群消费一次");
        assertEquals(key, records.get(0).key(), "Kafka record key 必须与保存快照一致");
    }

    @Test
    public void testRollbackLeavesNeitherBusinessNorOutboxRecord() {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String businessKey = "mock-business-rollback-" + suffix;
        String messageId = "mock-message-rollback-" + suffix;

        OutboxSaveResult result = transactionTemplate.execute(status -> {
            jdbcTemplate.update("INSERT INTO " + BUSINESS_TABLE + " (business_key) VALUES (?)", businessKey);
            OutboxSaveResult saved = outboxEngine.save(message("mock.outbox.rollback." + suffix,
                    "mock-key-" + suffix, messageId));
            status.setRollbackOnly();
            return saved;
        });

        assertNotNull(result, "标记回滚前仍应得到保存结果，避免掩盖事务边界");
        assertEquals(messageId, result.getMessageId(), "回滚前的保存结果应保留输入 messageId");
        assertEquals(Integer.valueOf(0), count(BUSINESS_TABLE, "business_key", businessKey),
                "回滚后不得留下业务行");
        assertEquals(Integer.valueOf(0), count(OUTBOX_TABLE, "message_id", messageId),
                "回滚后不得留下可由 Worker 发送的 Outbox 行");
    }

    @Test
    public void testReadOnlyTransactionRejectsOutboxWriteWithoutPersistingRecord() {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String messageId = "mock-message-read-only-" + suffix;
        TransactionTemplate readOnlyTransaction = new TransactionTemplate(transactionManager);
        readOnlyTransaction.setReadOnly(true);

        KafkaOutboxException exception = assertThrows(KafkaOutboxException.class,
                () -> readOnlyTransaction.execute(status -> outboxEngine.save(message("mock.outbox.read-only." + suffix,
                        "mock-key-" + suffix, messageId))), "只读本地事务必须拒绝 Outbox 写入");

        log.info("只读事务端到端拒绝结果，messageId={}, errorCode={}", messageId, exception.getErrorCode());
        assertEquals(ErrorCode.KAFKA_OUTBOX_003, exception.getErrorCode(), "只读事务必须返回事务边界错误码");
        assertEquals(Integer.valueOf(0), count(OUTBOX_TABLE, "message_id", messageId),
                "被拒绝的只读事务不得留下 Outbox 行");
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType("mock.transaction.created")
                .payload("mock-payload-" + key)
                .build();
    }

    private void awaitSent(String messageId) {
        long deadline = System.currentTimeMillis() + SENT_TIMEOUT_MS;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            status = jdbcTemplate.queryForObject("SELECT status FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                    String.class, messageId);
            if (OutboxStatus.SENT.getCode().equals(status)) {
                break;
            }
            sleep();
        }
        Long offset = jdbcTemplate.queryForObject("SELECT broker_offset FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Long.class, messageId);
        assertEquals(OutboxStatus.SENT.getCode(), status, "提交记录必须在超时前独立回写 SENT");
        assertNotNull(offset, "SENT 记录必须回填 broker offset");
    }

    private Integer tableCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?", Integer.class, tableName);
    }

    private Integer count(String tableName, String columnName, String value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Integer.class, value);
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
