package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.KafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support.FirstMarkSentFailureKafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support.KafkaOutboxEndToEndHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceScope;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.DefaultKafkaOutboxWorker;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
 * Kafka ACK 后 SENT 回写失败端到端测试
 *
 * @author surezzzzzz
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.enable=true",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false"
        })
public class KafkaOutboxAckPersistenceFailureEndToEndTest extends KafkaOutboxEndToEndTestSupport {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";
    private static final long STATE_TIMEOUT_MS = 30000L;
    private static final long POLL_INTERVAL_MS = 100L;

    @Autowired
    private KafkaOutboxEngine outboxEngine;

    @Autowired
    private KafkaOutboxRepository repository;

    @Autowired
    private KafkaOutboxMessageSerializer serializer;

    @Autowired
    private KafkaOutboxRetryPolicy retryPolicy;

    @Autowired
    private KafkaOutboxEventListener listener;

    @Autowired
    private KafkaOutboxTraceScope traceScope;

    @Autowired
    private KafkaPublisher publisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("kafkaOutboxWorker")
    private SmartLifecycle defaultWorker;

    private ThreadPoolTaskExecutor faultExecutor;
    private ThreadPoolTaskExecutor recoveryExecutor;

    @BeforeEach
    public void recreateOutboxTable() throws IOException {
        defaultWorker.stop();
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
        faultExecutor = executor("mock-ack-failure-");
        recoveryExecutor = executor("mock-ack-recovery-");
    }

    @AfterEach
    public void restoreWorkers() {
        if (faultExecutor != null) {
            faultExecutor.shutdown();
        }
        if (recoveryExecutor != null) {
            recoveryExecutor.shutdown();
        }
        if (!defaultWorker.isRunning()) {
            defaultWorker.start();
        }
    }

    @Test
    public void testAcknowledgedMessageIsReclaimedAndDeliveredExactlyTwiceWhenFirstMarkSentFails() {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-ack-failure-" + suffix;
        String messageId = "mock-message-ack-failure-" + suffix;
        createSharedTopic(topic);

        OutboxSaveResult saved = transactionTemplate.execute(status -> outboxEngine.save(message(topic, key, messageId)));
        assertNotNull(saved, "事务保存必须返回 Outbox 主键");

        FirstMarkSentFailureKafkaOutboxRepository faultRepository =
                new FirstMarkSentFailureKafkaOutboxRepository(repository, saved.getOutboxRecordId());
        DefaultKafkaOutboxWorker faultWorker = worker(faultRepository, faultExecutor);
        faultWorker.start();
        faultWorker.scanOnce();

        awaitProcessing(messageId);
        List<ConsumerRecord<String, String>> firstRecords = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        assertTrue(faultRepository.hasFailed(), "真实 Kafka ACK 后首次 markSent 必须被测试故障点拦截");
        assertEquals(1, firstRecords.size(), "首次 ACK 后必须已实际投递一条 Kafka record");
        assertEquals(key, firstRecords.get(0).key(), "首次投递 key 必须与快照一致");
        assertEquals(Integer.valueOf(1), queryInteger("attempt", messageId), "首次 claim 必须只累计一次尝试");
        assertEquals(OutboxStatus.PROCESSING.getCode(), queryString("status", messageId),
                "SENT 回写异常后必须保留 PROCESSING 供 lease 恢复");
        assertNotNull(queryString("owner_token", messageId), "回写异常后必须保留原 owner token");
        assertNotNull(queryTimestamp("lease_until", messageId), "回写异常后必须保留租约");
        assertNull(queryTimestamp("sent_at", messageId), "未成功回写时不得伪造 SENT 时间");
        assertNull(queryLong("broker_offset", messageId), "未成功回写时不得持久化 broker offset");

        awaitLeaseExpiry(messageId);
        DefaultKafkaOutboxWorker recoveryWorker = worker(repository, recoveryExecutor);
        recoveryWorker.start();
        recoveryWorker.scanOnce();
        awaitStatus(messageId, OutboxStatus.SENT.getCode());

        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 2);
        assertEquals(2, records.size(), "ACK 后回写失败的 at-least-once 边界必须产生且只产生两条投递");
        assertEquals(key, records.get(0).key(), "第一次投递 key 必须一致");
        assertEquals(key, records.get(1).key(), "恢复投递 key 必须一致");
        assertTrue(records.get(0).offset() != records.get(1).offset(), "两次投递必须拥有不同 broker offset");
        assertEquals(Integer.valueOf(2), queryInteger("attempt", messageId), "lease 恢复必须只新增一次 claim");
        assertNull(queryString("owner_token", messageId), "最终 SENT 后必须清理 owner token");
        assertNull(queryTimestamp("lease_until", messageId), "最终 SENT 后必须清理 lease");
        assertNull(queryString("last_error_code", messageId), "最终 SENT 后不得残留错误码");
        assertNotNull(queryLong("broker_offset", messageId), "最终 SENT 必须回填 broker offset");

        faultWorker.stop();
        recoveryWorker.stop();
    }

    private DefaultKafkaOutboxWorker worker(KafkaOutboxRepository workerRepository, ThreadPoolTaskExecutor executor) {
        SimpleKafkaOutboxProperties properties = new SimpleKafkaOutboxProperties();
        properties.getWorker().setConcurrency(1);
        properties.getWorker().setBatchSize(1);
        properties.getWorker().setScanIntervalMs(100L);
        properties.getWorker().setLeaseMs(1000L);
        properties.getWorker().setShutdownAwaitMs(1000L);
        properties.getSend().setTimeoutMs(500L);
        return new DefaultKafkaOutboxWorker(workerRepository, serializer, retryPolicy, listener, traceScope, publisher,
                properties, executor, Mockito.mock(TaskScheduler.class));
    }

    private ThreadPoolTaskExecutor executor(String threadPrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(threadPrefix);
        executor.initialize();
        return executor;
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType("mock.ack.persistence.failure")
                .payload("mock-payload-" + key)
                .build();
    }

    private void awaitProcessing(String messageId) {
        awaitStatus(messageId, OutboxStatus.PROCESSING.getCode());
    }

    private void awaitLeaseExpiry(String messageId) {
        long deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Integer expired = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + OUTBOX_TABLE
                    + " WHERE message_id = ? AND lease_until < CURRENT_TIMESTAMP(3)", Integer.class, messageId);
            if (Integer.valueOf(1).equals(expired)) {
                return;
            }
            sleep();
        }
        throw new AssertionError("租约必须在超时前按 MySQL 时钟过期");
    }

    private void awaitStatus(String messageId, String expectedStatus) {
        long deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            status = queryString("status", messageId);
            if (expectedStatus.equals(status)) {
                return;
            }
            sleep();
        }
        assertEquals(expectedStatus, status, "Outbox 状态必须在超时前到达预期值");
    }

    private Integer queryInteger(String column, String messageId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Integer.class, messageId);
    }

    private Long queryLong(String column, String messageId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                Long.class, messageId);
    }

    private String queryString(String column, String messageId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                String.class, messageId);
    }

    private java.sql.Timestamp queryTimestamp(String column, String messageId) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE message_id = ?",
                java.sql.Timestamp.class, messageId);
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
