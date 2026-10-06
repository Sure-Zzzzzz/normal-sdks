package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 提交后回调集成测试
 *
 * @author surezzzzzz
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.enable=false",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false"
        })
@Import(KafkaOutboxAfterCommitIntegrationTest.ListenerConfiguration.class)
public class KafkaOutboxAfterCommitIntegrationTest {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";

    @Autowired
    private KafkaOutboxEngine outboxEngine;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingListener listener;

    @BeforeEach
    public void recreateOutboxTable() throws IOException {
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
        listener.reset();
    }

    @Test
    public void shouldNotifySavedExactlyOnceOnlyAfterCommittedOutboxRowIsVisible() {
        String messageId = "mock-after-commit-success";
        String topic = "mock.after.commit.topic";
        OutboxSaveResult saved = transactionTemplate.execute(status -> {
            OutboxSaveResult result = outboxEngine.save(message(topic, "mock-key-success", messageId));
            assertEquals(0, listener.contexts().size(), "事务内部不得提前触发 onSaved");
            assertEquals(Integer.valueOf(1), count(messageId), "当前事务必须可见已保存快照");
            return result;
        });

        assertNotNull(saved, "提交事务必须返回保存结果");
        assertEquals(Integer.valueOf(1), count(messageId), "提交后必须持久化唯一快照");
        assertEquals(1, listener.contexts().size(), "提交后必须恰好触发一次 onSaved");
        assertEquals(Integer.valueOf(1), listener.visibleRowCounts().get(0), "onSaved 观察到的快照必须已提交");
        OutboxEventContext context = listener.contexts().get(0);
        assertEquals(saved.getOutboxRecordId(), context.getRecordId(), "回调必须携带真实 Outbox 主键");
        assertEquals(messageId, context.getMessageId(), "回调必须携带稳定 messageId");
        assertEquals(topic, context.getTopic(), "回调必须携带 topic");
        assertEquals("PENDING", context.getStatus(), "初始回调必须描述 PENDING 快照");
        assertEquals(Integer.valueOf(0), context.getAttempt(), "初始回调 attempt 必须为零");
        assertEquals(Integer.valueOf(1), context.getSchemaVersion(), "回调必须携带当前 schemaVersion");
        assertTrue(context.getDatasourceKey() == null, "未指定 datasourceKey 不得在回调中伪造值");
    }

    @Test
    public void shouldNeverNotifyOrPersistWhenTransactionRollsBack() {
        String messageId = "mock-after-commit-rollback";

        assertThrows(IllegalStateException.class, () -> transactionTemplate.execute(status -> {
            outboxEngine.save(message("mock.after.commit.rollback", "mock-key-rollback", messageId));
            assertEquals(0, listener.contexts().size(), "回滚前不得触发 onSaved");
            throw new IllegalStateException("mock-rollback");
        }), "业务事务抛出的回滚异常必须保持可见");

        assertEquals(Integer.valueOf(0), count(messageId), "回滚事务不得留下 Outbox 快照");
        assertEquals(0, listener.contexts().size(), "回滚事务不得触发 onSaved");
        assertEquals(0, listener.visibleRowCounts().size(), "回滚事务不得产生提交可见性记录");
    }

    @Test
    public void shouldIsolateListenerFailureAndContinueEveryCallbackInOneCommit() {
        String firstMessageId = "mock-after-commit-first";
        String secondMessageId = "mock-after-commit-second";
        listener.failFirstSaved();

        List<OutboxSaveResult> results = assertDoesNotThrow(() -> transactionTemplate.execute(status -> {
            List<OutboxSaveResult> saved = new ArrayList<>();
            saved.add(outboxEngine.save(message("mock.after.commit.first", "mock-key-first", firstMessageId)));
            saved.add(outboxEngine.save(message("mock.after.commit.second", "mock-key-second", secondMessageId)));
            assertEquals(0, listener.contexts().size(), "事务内部不得触发任意 onSaved");
            return saved;
        }), "listener 异常不得影响已经提交的业务事务");

        assertNotNull(results, "提交事务必须返回所有保存结果");
        assertEquals(Integer.valueOf(1), count(firstMessageId), "第一个快照必须提交一次");
        assertEquals(Integer.valueOf(1), count(secondMessageId), "第二个快照必须提交一次");
        assertEquals(2, listener.contexts().size(), "首个 listener 异常不得阻断同事务后续回调");
        assertEquals(Integer.valueOf(1), listener.visibleRowCounts().get(0), "首个回调时其快照必须已提交");
        assertEquals(Integer.valueOf(1), listener.visibleRowCounts().get(1), "第二个回调时其快照必须已提交");
        assertEquals(firstMessageId, listener.contexts().get(0).getMessageId(), "回调顺序必须保持保存顺序");
        assertEquals(secondMessageId, listener.contexts().get(1).getMessageId(), "首个异常不得吞掉第二个独立回调");
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType("mock.after.commit")
                .payload("mock-payload-" + key)
                .build();
    }

    private Integer count(String messageId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Integer.class, messageId);
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "docs", "01_schema.sql");
    }

    @TestConfiguration
    public static class ListenerConfiguration {
        @Bean
        @Primary
        public RecordingListener kafkaOutboxEventListener(JdbcTemplate jdbcTemplate) {
            return new RecordingListener(jdbcTemplate);
        }
    }

    public static final class RecordingListener implements KafkaOutboxEventListener {
        private final JdbcTemplate jdbcTemplate;
        private final List<OutboxEventContext> contexts = new ArrayList<>();
        private final List<Integer> visibleRowCounts = new ArrayList<>();
        private boolean failFirstSaved;

        private RecordingListener(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        private synchronized void reset() {
            contexts.clear();
            visibleRowCounts.clear();
            failFirstSaved = false;
        }

        private synchronized void failFirstSaved() {
            failFirstSaved = true;
        }

        private synchronized List<OutboxEventContext> contexts() {
            return new ArrayList<>(contexts);
        }

        private synchronized List<Integer> visibleRowCounts() {
            return new ArrayList<>(visibleRowCounts);
        }

        @Override
        public synchronized void onSaved(OutboxEventContext context) {
            contexts.add(context);
            visibleRowCounts.add(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + OUTBOX_TABLE + " WHERE id = ?", Integer.class, context.getRecordId()));
            if (failFirstSaved) {
                failFirstSaved = false;
                throw new IllegalStateException("mock-listener-failure");
            }
        }

        @Override
        public void onClaimed(OutboxEventContext context) {
        }

        @Override
        public void onSent(OutboxEventContext context) {
        }

        @Override
        public void onRetry(OutboxEventContext context) {
        }

        @Override
        public void onPoison(OutboxEventContext context) {
        }

        @Override
        public void onLeaseLost(OutboxEventContext context) {
        }

        @Override
        public void onCleanup(OutboxCleanupContext context) {
        }
    }
}
