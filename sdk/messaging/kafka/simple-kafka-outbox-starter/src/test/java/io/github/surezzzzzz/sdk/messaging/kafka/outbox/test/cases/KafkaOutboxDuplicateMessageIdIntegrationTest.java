package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support.KafkaOutboxEndToEndHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 重复 messageId 集成测试
 *
 * @author surezzzzzz
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.scan-interval-ms=50"
        })
public class KafkaOutboxDuplicateMessageIdIntegrationTest extends KafkaOutboxEndToEndTestSupport {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";
    private static final long STATE_TIMEOUT_MS = 30000L;

    @Autowired
    private KafkaOutboxEngine outboxEngine;

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
    }

    @Test
    public void shouldRejectDuplicateMessageIdWithinOneTransactionWithoutOverwritingFirstSnapshot() {
        String messageId = "mock-duplicate-same-transaction";
        OutboxSaveResult first = transactionTemplate.execute(status -> {
            OutboxSaveResult saved = outboxEngine.save(message("mock.topic.first", "mock-key-first", messageId, "first"));
            KafkaOutboxException exception = assertThrows(KafkaOutboxException.class,
                    () -> outboxEngine.save(message("mock.topic.second", "mock-key-second", messageId, "second")),
                    "同一事务的重复 messageId 必须失败");
            assertEquals(ErrorCode.KAFKA_OUTBOX_004, exception.getErrorCode(), "重复必须转换为稳定业务错误码");
            return saved;
        });

        assertNotNull(first, "首个快照必须正常保存");
        assertEquals(Integer.valueOf(1), count(messageId), "同一事务只允许保留一个快照");
        assertEquals("mock.topic.first", stringValue("topic", messageId), "失败写入不得覆盖首个 topic");
        assertEquals("mock-key-first", stringValue("record_key", messageId), "失败写入不得覆盖首个 key");
        assertEquals("first", stringValue("payload_json", messageId), "失败写入不得覆盖首个 payload");
        assertEquals(OutboxStatus.PENDING.getCode(), stringValue("status", messageId), "失败写入不得改变首个状态");
        assertEquals(Long.valueOf(0L), longValue("version", messageId), "失败写入不得改变首个 version");
    }

    @Test
    public void shouldRejectDuplicateMessageIdAcrossCommittedTransactionsWithoutOverwritingSnapshot() {
        String messageId = "mock-duplicate-committed-transaction";
        OutboxSaveResult first = save(message("mock.topic.committed.first", "mock-key-first", messageId, "first"));

        KafkaOutboxException exception = assertThrows(KafkaOutboxException.class,
                () -> save(message("mock.topic.committed.second", "mock-key-second", messageId, "second")),
                "已提交快照的重复 messageId 必须失败");

        assertNotNull(first, "首个已提交快照必须返回主键");
        assertEquals(ErrorCode.KAFKA_OUTBOX_004, exception.getErrorCode(), "跨事务重复必须转换为稳定业务错误码");
        assertEquals(Integer.valueOf(1), count(messageId), "跨事务重复不得产生第二个快照");
        assertEquals("mock.topic.committed.first", stringValue("topic", messageId), "重复写入不得覆盖首个 topic");
        assertEquals("mock-key-first", stringValue("record_key", messageId), "重复写入不得覆盖首个 key");
        assertEquals("first", stringValue("payload_json", messageId), "重复写入不得覆盖首个 payload");
    }

    @Test
    public void shouldCommitOneConcurrentWinnerAndDeliverItExactlyOnce() throws Exception {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-duplicate-concurrent-" + suffix;
        String messageId = "mock-duplicate-concurrent-" + suffix;
        createSharedTopic(topic);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<OutboxSaveResult> firstResult = new AtomicReference<>();
        AtomicReference<OutboxSaveResult> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread first = concurrentSave("duplicate-first", ready, start, () ->
                save(message(topic, key, messageId, "first")), firstResult, firstFailure);
        Thread second = concurrentSave("duplicate-second", ready, start, () ->
                save(message(topic, key, messageId, "second")), secondResult, secondFailure);
        first.start();
        second.start();
        assertTrue(ready.await(5L, TimeUnit.SECONDS), "两个事务必须同时抵达写入起点");
        start.countDown();
        first.join(10000L);
        second.join(10000L);
        assertTrue(!first.isAlive() && !second.isAlive(), "并发事务必须在超时前完成");

        assertEquals(1, successes(firstResult, secondResult), "并发重复写入必须恰好一个事务提交");
        KafkaOutboxException duplicate = duplicateFailure(firstFailure.get(), secondFailure.get());
        assertEquals(ErrorCode.KAFKA_OUTBOX_004, duplicate.getErrorCode(), "失败事务必须收到稳定重复错误码");
        assertEquals(Integer.valueOf(1), count(messageId), "并发重复写入必须只留一份快照");
        String payload = stringValue("payload_json", messageId);
        assertTrue("first".equals(payload) || "second".equals(payload), "唯一快照必须完整保留某个胜者 payload");

        awaitStatus(messageId, OutboxStatus.SENT.getCode());
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        assertEquals(1, records.size(), "并发 winner 必须且只能投递一条 Kafka record");
        assertEquals(key, records.get(0).key(), "并发 winner 投递必须保持 Kafka key");
        assertEquals(Integer.valueOf(1), integer("attempt", messageId), "唯一 winner 只允许被 claim 一次");
    }

    private Thread concurrentSave(String name, CountDownLatch ready, CountDownLatch start,
                                  SaveOperation operation, AtomicReference<OutboxSaveResult> result,
                                  AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            ready.countDown();
            try {
                if (!start.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待并发写入起点超时");
                }
                result.set(operation.save());
            } catch (Throwable e) {
                failure.set(e);
            }
        }, name);
    }

    private int successes(AtomicReference<OutboxSaveResult> first, AtomicReference<OutboxSaveResult> second) {
        return (first.get() == null ? 0 : 1) + (second.get() == null ? 0 : 1);
    }

    private KafkaOutboxException duplicateFailure(Throwable first, Throwable second) {
        Throwable failure = first == null ? second : first;
        assertNotNull(failure, "并发重复写入必须产生一个失败事务");
        assertTrue(failure instanceof KafkaOutboxException, "失败事务必须收到 KafkaOutboxException");
        return (KafkaOutboxException) failure;
    }

    private OutboxSaveResult save(KafkaPublishMessage<String> message) {
        return transactionTemplate.execute(status -> outboxEngine.save(message));
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId, String payload) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType("mock.duplicate.message-id")
                .payload(payload)
                .build();
    }

    private void awaitStatus(String messageId, String expectedStatus) {
        long deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            status = stringValue("status", messageId);
            if (expectedStatus.equals(status)) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Outbox 状态被中断", e);
            }
        }
        assertEquals(expectedStatus, status, "Outbox 状态必须在超时前到达预期值");
    }

    private Integer count(String messageId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Integer.class, messageId);
    }

    private Integer integer(String column, String messageId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Integer.class, messageId);
    }

    private Long longValue(String column, String messageId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Long.class, messageId);
    }

    private String stringValue(String column, String messageId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                String.class, messageId);
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "docs", "01_schema.sql");
    }

    private interface SaveOperation {
        OutboxSaveResult save();
    }
}
