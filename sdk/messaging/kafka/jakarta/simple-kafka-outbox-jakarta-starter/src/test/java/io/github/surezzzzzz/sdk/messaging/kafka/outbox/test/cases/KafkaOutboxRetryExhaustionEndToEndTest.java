package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
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
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 重试耗尽端到端测试
 *
 * @author surezzzzzz
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.scan-interval-ms=50",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.send.timeout-ms=5000",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.max-attempts=2",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.initial-interval-ms=10",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.multiplier=1.0",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.max-interval-ms=10",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.jitter-factor=0.0"
        })
public class KafkaOutboxRetryExhaustionEndToEndTest extends KafkaOutboxEndToEndTestSupport {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";
    private static final long STATE_TIMEOUT_MS = 30000L;
    private static final long POLL_INTERVAL_MS = 100L;

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
    public void shouldPoisonDeadRouteAfterExactMaxAttemptsWithoutBlockingLiveRoute() {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String deadMessageId = "mock-message-dead-" + suffix;
        String deadKey = "mock-key-dead-" + suffix;
        String liveMessageId = "mock-message-live-" + suffix;
        String liveKey = "mock-key-live-" + suffix;
        createSharedTopic(topic);

        OutboxSaveResult dead = save(message(topic, deadKey, deadMessageId, "mock.outbox.dead.delivery",
                "mock.outbox.dead." + suffix));
        OutboxSaveResult live = save(message(topic, liveKey, liveMessageId, "mock.outbox.live.delivery", null));
        assertNotNull(dead, "不可达路由保存必须返回 Outbox 主键");
        assertNotNull(live, "有效路由保存必须返回 Outbox 主键");

        awaitStatus(deadMessageId, OutboxStatus.POISON.getCode());
        awaitStatus(liveMessageId, OutboxStatus.SENT.getCode());
        List<ConsumerRecord<String, String>> liveRecords = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(liveKey), 1);

        assertEquals(Integer.valueOf(2), integer("attempt", deadMessageId), "不可达路由必须恰好尝试 maxAttempts 次");
        assertEquals(OutboxStatus.POISON.getCode(), stringValue("status", deadMessageId),
                "重试耗尽必须进入 POISON 终态");
        assertNull(stringValue("owner_token", deadMessageId), "POISON 必须清理 owner token");
        assertNull(timestamp("lease_until", deadMessageId), "POISON 必须清理 lease");
        assertNotNull(stringValue("last_error_code", deadMessageId), "POISON 必须保留稳定错误码");
        assertTrue(stringValue("last_error_summary", deadMessageId).length() > 0,
                "POISON 必须保留脱敏错误摘要");
        Integer terminalAttempt = integer("attempt", deadMessageId);
        Long terminalVersion = longValue("version", deadMessageId);
        sleep();
        sleep();
        assertEquals(terminalAttempt, integer("attempt", deadMessageId), "终态扫描不得额外递增 attempt");
        assertEquals(terminalVersion, longValue("version", deadMessageId), "终态扫描不得额外递增 version");
        assertEquals(OutboxStatus.POISON.getCode(), stringValue("status", deadMessageId), "终态扫描不得离开 POISON");
        assertEquals(1, liveRecords.size(), "不可达路由不得阻塞有效路由的精确一次投递");
        assertEquals(liveKey, liveRecords.get(0).key(), "有效路由投递必须保持稳定 key");
        assertNotNull(longValue("broker_offset", liveMessageId), "有效路由 SENT 必须保存 broker offset");
    }

    private OutboxSaveResult save(KafkaPublishMessage<String> message) {
        return transactionTemplate.execute(status -> outboxEngine.save(message));
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId, String messageType,
                                                String routeKey) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType(messageType)
                .routeKey(routeKey)
                .payload("mock-payload-" + key)
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
            sleep();
        }
        assertEquals(expectedStatus, status, "Outbox 状态必须在超时前到达预期终态");
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

    private Timestamp timestamp(String column, String messageId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Timestamp.class, messageId);
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
