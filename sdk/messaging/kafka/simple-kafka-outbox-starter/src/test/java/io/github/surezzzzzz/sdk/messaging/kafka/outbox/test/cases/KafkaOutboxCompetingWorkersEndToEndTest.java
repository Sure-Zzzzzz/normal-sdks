package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.KafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support.KafkaOutboxEndToEndHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceScope;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.DefaultKafkaOutboxWorker;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Kafka Outbox 独立 Worker 抢占端到端测试
 *
 * @author surezzzzzz
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.enable=true",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false"
        })
public class KafkaOutboxCompetingWorkersEndToEndTest extends KafkaOutboxEndToEndTestSupport {

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

    private ThreadPoolTaskExecutor firstExecutor;
    private ThreadPoolTaskExecutor secondExecutor;
    private DefaultKafkaOutboxWorker firstWorker;
    private DefaultKafkaOutboxWorker secondWorker;

    @BeforeEach
    public void recreateOutboxTable() throws IOException {
        defaultWorker.stop();
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
        firstExecutor = executor("competing-worker-a-");
        secondExecutor = executor("competing-worker-b-");
    }

    @AfterEach
    public void restoreWorkers() {
        if (firstWorker != null) {
            firstWorker.stop();
        }
        if (secondWorker != null) {
            secondWorker.stop();
        }
        if (firstExecutor != null) {
            firstExecutor.shutdown();
        }
        if (secondExecutor != null) {
            secondExecutor.shutdown();
        }
        if (!defaultWorker.isRunning()) {
            defaultWorker.start();
        }
    }

    @Test
    public void shouldRejectAllStaleOwnerTransitionsAfterLeaseIsReclaimed() throws Exception {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-stale-owner-" + suffix;
        String messageId = "mock-message-stale-owner-" + suffix;
        createSharedTopic(topic);
        OutboxSaveResult saved = transactionTemplate.execute(status -> outboxEngine.save(message(topic, key, messageId)));
        assertNotNull(saved, "事务保存必须返回真实 Outbox 主键");

        OutboxRecordEntity firstOwner = only(repository.claim(1, 1_000_000L));
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET lease_until = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) "
                + "WHERE id = ?", firstOwner.getId());
        OutboxRecordEntity secondOwner = only(repository.claim(1, 10_000_000L));
        assertEquals(firstOwner.getId(), secondOwner.getId(), "过期租约必须由新 owner 重新领取同一记录");
        assertTrue(!firstOwner.getOwnerToken().equals(secondOwner.getOwnerToken()), "重领必须生成全新 owner token");
        assertEquals(Long.valueOf(firstOwner.getVersion() + 1L), secondOwner.getVersion(),
                "重领必须精确递增 version");

        firstOwner.setBrokerTopic(topic);
        firstOwner.setBrokerPartition(0);
        firstOwner.setBrokerOffset(999L);
        firstOwner.setBrokerTimestamp(System.currentTimeMillis());
        assertTrue(!repository.markSent(firstOwner), "陈旧 owner 不得回写 SENT");
        assertTrue(!repository.markRetry(firstOwner, 1_000_000L, "STALE_RETRY", "stale retry"),
                "陈旧 owner 不得回写 RETRY_WAIT");
        assertTrue(!repository.markPoison(firstOwner, "STALE_POISON", "stale poison"),
                "陈旧 owner 不得回写 POISON");
        assertTrue(!repository.releaseBeforeSend(firstOwner, "STALE_STOP", "stale stop"),
                "陈旧 owner 不得释放新 owner 的租约");
        assertEquals(OutboxStatus.PROCESSING.getCode(), stringValue("status", messageId),
                "陈旧 mutation 后记录必须仍由新 owner PROCESSING");
        assertEquals(secondOwner.getOwnerToken(), stringValue("owner_token", messageId),
                "陈旧 mutation 不得覆盖新 owner token");
        assertEquals(secondOwner.getVersion(), longValue("version", messageId),
                "陈旧 mutation 不得改变新 owner version");
        assertNull(stringValue("last_error_code", messageId), "陈旧 mutation 不得写入错误码");
        assertNull(longValue("broker_offset", messageId), "陈旧 mutation 不得写入 broker offset");

        KafkaPublishResult result = publisher.publish(serializer.deserialize(secondOwner)).get();
        secondOwner.setBrokerTopic(result.getTopic());
        secondOwner.setBrokerPartition(result.getPartition());
        secondOwner.setBrokerOffset(result.getOffset());
        secondOwner.setBrokerTimestamp(result.getTimestamp());
        assertTrue(repository.markSent(secondOwner), "当前 owner 必须能够完成 SENT 回写");
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        assertEquals(1, records.size(), "新 owner 实际发送后必须且只能观察到一条 Kafka record");
        assertEquals(key, records.get(0).key(), "新 owner 发送不得改变 Kafka record key");
        assertEquals(OutboxStatus.SENT.getCode(), stringValue("status", messageId), "新 owner 必须完成 SENT");
        assertEquals(Long.valueOf(secondOwner.getVersion() + 1L), longValue("version", messageId),
                "SENT 回写必须精确递增 version");
    }

    @Test
    public void shouldNotifyLeaseLostOnceWhenStaleWorkerCompletesAfterReclaim() throws Exception {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-worker-stale-owner-" + suffix;
        String messageId = "mock-message-worker-stale-owner-" + suffix;
        createSharedTopic(topic);
        OutboxSaveResult saved = transactionTemplate.execute(status -> outboxEngine.save(message(topic, key, messageId)));
        assertNotNull(saved, "事务保存必须返回真实 Outbox 主键");

        DelayedAckKafkaPublisher delayedPublisher = new DelayedAckKafkaPublisher(publisher);
        CountingListener firstListener = new CountingListener();
        CountingListener secondListener = new CountingListener();
        firstWorker = worker(firstExecutor, firstListener, delayedPublisher, 1000L);
        secondWorker = worker(secondExecutor, secondListener, publisher, 10000L);
        firstWorker.start();
        firstWorker.scanOnce();
        assertTrue(delayedPublisher.awaitBrokerAcknowledged(), "Worker A 必须先获得真实 broker ACK");
        awaitStatus(messageId, OutboxStatus.PROCESSING.getCode());
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE
                        + " SET lease_until = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE message_id = ?",
                messageId);

        secondWorker.start();
        secondWorker.scanOnce();
        awaitStatus(messageId, OutboxStatus.SENT.getCode());
        assertEquals(1, secondListener.sent.get(), "Worker B 必须完成唯一一次 SENT 回写");
        assertEquals(0, secondListener.leaseLost.get(), "Worker B 不得丢失其新租约");

        delayedPublisher.releaseAck();
        awaitCounter(firstListener.leaseLost, 1, "Worker A 的陈旧回写必须只触发一次 leaseLost");
        assertEquals(0, firstListener.sent.get(), "陈旧 Worker A 不得触发 SENT 事件");
        assertEquals(OutboxStatus.SENT.getCode(), stringValue("status", messageId), "陈旧回写不得覆盖 Worker B 的 SENT");
        assertEquals(Integer.valueOf(2), integer("attempt", messageId), "重领必须只额外增加一次 attempt");
        assertEquals(Long.valueOf(3L), longValue("version", messageId), "初始记录必须仅经历 A claim、B claim、B SENT");
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 2);
        assertEquals(2, records.size(), "两个已获 broker ACK 的 owner 必须精确观察到两条 Kafka record");
        assertTrue(records.get(0).offset() != records.get(1).offset(), "两条记录必须拥有不同 broker offset");

        firstWorker.stop();
        secondWorker.stop();
    }

    @Test
    public void shouldDeliverExactlyOnceWhenIndependentWorkersRaceToClaimOneRecord() throws Exception {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-competing-" + suffix;
        String messageId = "mock-message-competing-" + suffix;
        createSharedTopic(topic);
        OutboxSaveResult saved = transactionTemplate.execute(status -> outboxEngine.save(message(topic, key, messageId)));
        assertNotNull(saved, "事务保存必须返回真实 Outbox 主键");

        CountingListener listener = new CountingListener();
        firstWorker = worker(firstExecutor, listener);
        secondWorker = worker(secondExecutor, listener);
        firstWorker.start();
        secondWorker.start();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Thread firstScan = concurrentScan(firstWorker, ready, start, "competing-scan-a");
        Thread secondScan = concurrentScan(secondWorker, ready, start, "competing-scan-b");
        firstScan.start();
        secondScan.start();
        assertTrue(ready.await(5L, TimeUnit.SECONDS), "两个独立 Worker 必须同时抵达竞争起点");
        start.countDown();
        firstScan.join(5000L);
        secondScan.join(5000L);
        assertTrue(!firstScan.isAlive() && !secondScan.isAlive(), "两个竞争扫描必须在超时前结束");

        awaitStatus(messageId, OutboxStatus.SENT.getCode());
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        assertEquals(1, records.size(), "两个独立 Worker 抢占同一记录后必须且只能投递一条 Kafka record");
        assertEquals(key, records.get(0).key(), "竞争领取不得改变 Kafka record key");
        assertEquals(Integer.valueOf(1), integer("attempt", messageId), "同一记录只能被成功领取一次");
        assertEquals(Long.valueOf(2L), longValue("version", messageId), "初始记录必须恰好经历 claim 和 SENT 两次 version 迁移");
        assertNull(stringValue("owner_token", messageId), "最终 SENT 不得残留 owner token");
        assertNull(timestamp("lease_until", messageId), "最终 SENT 不得残留 lease");
        assertNull(stringValue("last_error_code", messageId), "正常竞争成功后不得残留错误码");
        assertNotNull(longValue("broker_offset", messageId), "最终 SENT 必须保存 broker offset");
        assertEquals(1, listener.claimed.get(), "竞争记录必须只触发一次 claimed 事件");
        assertEquals(1, listener.sent.get(), "竞争记录必须只触发一次 sent 事件");
        assertEquals(0, listener.leaseLost.get(), "成功竞争不得产生 leaseLost 事件");
    }

    private OutboxRecordEntity only(List<OutboxRecordEntity> records) {
        assertEquals(1, records.size(), "领取操作必须恰好返回一条记录");
        return records.get(0);
    }

    private DefaultKafkaOutboxWorker worker(ThreadPoolTaskExecutor executor, KafkaOutboxEventListener listener) {
        return worker(executor, listener, publisher, 10000L);
    }

    private DefaultKafkaOutboxWorker worker(ThreadPoolTaskExecutor executor, KafkaOutboxEventListener listener,
                                            KafkaPublisher workerPublisher, long leaseMs) {
        SimpleKafkaOutboxProperties properties = new SimpleKafkaOutboxProperties();
        properties.getWorker().setConcurrency(1);
        properties.getWorker().setBatchSize(1);
        properties.getWorker().setScanIntervalMs(100L);
        properties.getWorker().setLeaseMs(leaseMs);
        properties.getWorker().setShutdownAwaitMs(1000L);
        properties.getSend().setTimeoutMs(5000L);
        return new DefaultKafkaOutboxWorker(repository, serializer, retryPolicy, listener, traceScope, workerPublisher,
                properties, executor, mock(TaskScheduler.class));
    }

    private Thread concurrentScan(DefaultKafkaOutboxWorker worker, CountDownLatch ready, CountDownLatch start,
                                  String threadName) {
        return new Thread(() -> {
            ready.countDown();
            try {
                if (!start.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待 Worker 并发起点超时");
                }
                worker.scanOnce();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Worker 竞争扫描被中断", e);
            }
        }, threadName);
    }

    private ThreadPoolTaskExecutor executor(String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(prefix);
        executor.initialize();
        return executor;
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType("mock.competing.worker")
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
        assertEquals(expectedStatus, status, "Outbox 状态必须在超时前到达 SENT");
    }

    private void awaitCounter(AtomicInteger counter, int expected, String message) {
        long deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (counter.get() == expected) {
                return;
            }
            sleep();
        }
        assertEquals(expected, counter.get(), message);
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

    private static final class DelayedAckKafkaPublisher implements KafkaPublisher {
        private final KafkaPublisher delegate;
        private final CountDownLatch brokerAcknowledged = new CountDownLatch(1);
        private final SettableListenableFuture<KafkaPublishResult> delayed = new SettableListenableFuture<>();
        private volatile KafkaPublishResult acknowledgedResult;
        private volatile Throwable acknowledgedFailure;

        private DelayedAckKafkaPublisher(KafkaPublisher delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> ListenableFuture<KafkaPublishResult> publish(String topic, T payload) {
            return delegate.publish(topic, payload);
        }

        @Override
        public <T> ListenableFuture<KafkaPublishResult> publish(String topic, String key, T payload) {
            return delegate.publish(topic, key, payload);
        }

        @Override
        public <T> ListenableFuture<KafkaPublishResult> publish(KafkaPublishMessage<T> message) {
            ListenableFuture<KafkaPublishResult> actual = delegate.publish(message);
            actual.addCallback(result -> {
                acknowledgedResult = result;
                brokerAcknowledged.countDown();
            }, failure -> {
                acknowledgedFailure = failure;
                brokerAcknowledged.countDown();
            });
            return delayed;
        }

        @Override
        public <T> ListenableFuture<KafkaPublishResult> publishByRouteKey(String routeKey, KafkaPublishMessage<T> message) {
            return delegate.publishByRouteKey(routeKey, message);
        }

        @Override
        public <T> ListenableFuture<KafkaPublishResult> publishOn(String datasourceKey, KafkaPublishMessage<T> message) {
            return delegate.publishOn(datasourceKey, message);
        }

        @Override
        public <T> KafkaPublishResult publishAndWait(KafkaPublishMessage<T> message) {
            return delegate.publishAndWait(message);
        }

        private boolean awaitBrokerAcknowledged() throws InterruptedException {
            return brokerAcknowledged.await(10L, TimeUnit.SECONDS);
        }

        private void releaseAck() {
            if (brokerAcknowledged.getCount() != 0L) {
                throw new IllegalStateException("真实 broker ACK 未完成，不能释放延迟回写");
            }
            if (acknowledgedFailure != null) {
                delayed.setException(acknowledgedFailure);
            } else {
                delayed.set(acknowledgedResult);
            }
        }
    }

    private static final class CountingListener implements KafkaOutboxEventListener {
        private final AtomicInteger claimed = new AtomicInteger();
        private final AtomicInteger sent = new AtomicInteger();
        private final AtomicInteger leaseLost = new AtomicInteger();

        @Override
        public void onSaved(OutboxEventContext context) {
        }

        @Override
        public void onClaimed(OutboxEventContext context) {
            claimed.incrementAndGet();
        }

        @Override
        public void onSent(OutboxEventContext context) {
            sent.incrementAndGet();
        }

        @Override
        public void onRetry(OutboxEventContext context) {
        }

        @Override
        public void onPoison(OutboxEventContext context) {
        }

        @Override
        public void onLeaseLost(OutboxEventContext context) {
            leaseLost.incrementAndGet();
        }

        @Override
        public void onCleanup(OutboxCleanupContext context) {
        }
    }
}
