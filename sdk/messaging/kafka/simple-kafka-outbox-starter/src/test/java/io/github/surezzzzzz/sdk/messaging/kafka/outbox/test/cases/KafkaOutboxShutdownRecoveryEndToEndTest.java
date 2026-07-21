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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 停机恢复端到端测试
 *
 * @author surezzzzzz
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.enable=false",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false"
        })
public class KafkaOutboxShutdownRecoveryEndToEndTest extends KafkaOutboxEndToEndTestSupport {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";
    private static final long STATE_TIMEOUT_MS = 30000L;
    private static final long POLL_INTERVAL_MS = 50L;

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

    private ThreadPoolTaskExecutor stoppingExecutor;
    private ThreadPoolTaskExecutor recoveryExecutor;
    private ThreadPoolTaskExecutor publishingExecutor;

    @BeforeEach
    public void recreateOutboxTable() throws IOException {
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    @AfterEach
    public void restoreWorkers() {
        shutdown(stoppingExecutor);
        shutdown(recoveryExecutor);
        shutdown(publishingExecutor);
    }

    @Test
    public void shouldReleaseBeforePublishAndDeliverExactlyOnceAfterReplacementWorkerStarts() throws Exception {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-shutdown-before-publish-" + suffix;
        String messageId = "mock-message-shutdown-before-publish-" + suffix;
        createSharedTopic(topic);
        OutboxSaveResult saved = save(message(topic, key, messageId));
        assertNotNull(saved, "事务保存必须返回 Outbox 主键");

        CountDownLatch taskEntered = new CountDownLatch(1);
        CountDownLatch allowProcess = new CountDownLatch(1);
        stoppingExecutor = blockingExecutor("mock-shutdown-before-publish-", taskEntered, allowProcess);
        DefaultKafkaOutboxWorker stoppingWorker = worker(stoppingExecutor, publisher, 5000L);
        stoppingWorker.start();
        stoppingWorker.scanOnce();
        assertTrue(taskEntered.await(5L, TimeUnit.SECONDS), "已 claim 的任务必须在 process 前受控阻塞");
        assertEquals(OutboxStatus.PROCESSING.getCode(), stringValue("status", messageId), "任务阻塞时必须持有 PROCESSING 租约");

        Thread stopThread = new Thread(stoppingWorker::stop, "mock-stop-before-publish");
        stopThread.start();
        awaitWorkerStopped(stoppingWorker);
        allowProcess.countDown();
        stopThread.join(5000L);
        assertTrue(!stopThread.isAlive(), "停机线程必须等待释放任务后退出");

        awaitStatus(messageId, OutboxStatus.RETRY_WAIT.getCode());
        assertEquals(Integer.valueOf(1), integer("attempt", messageId), "发送前停机释放不得伪造额外 attempt");
        assertNull(stringValue("owner_token", messageId), "发送前停机释放必须清理 owner token");
        assertNull(timestamp("lease_until", messageId), "发送前停机释放必须清理 lease");
        assertEquals("KAFKA_OUTBOX_SHUTDOWN_RELEASE", stringValue("last_error_code", messageId),
                "发送前停机释放必须保留稳定错误码");
        assertEquals(Integer.valueOf(0), recordsInTopic(topic, key), "发送前停止不得产生 Kafka record");

        recoveryExecutor = executor("mock-shutdown-recovery-");
        DefaultKafkaOutboxWorker recoveryWorker = worker(recoveryExecutor, publisher, 1000L);
        recoveryWorker.start();
        recoveryWorker.scanOnce();
        awaitStatus(messageId, OutboxStatus.SENT.getCode());
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        assertEquals(1, records.size(), "发送前停机恢复后必须且只能投递一条 Kafka record");
        assertEquals(key, records.get(0).key(), "恢复投递必须保持 Kafka key");
        assertEquals(Integer.valueOf(0), Integer.valueOf(records.get(0).partition()), "单分区 topic 的恢复投递必须写入分区 0");
        assertEquals(Integer.valueOf(records.get(0).partition()), integer("broker_partition", messageId),
                "SENT 回写的 broker partition 必须与真实 Kafka record 一致");
        assertEquals(Long.valueOf(records.get(0).offset()), longValue("broker_offset", messageId),
                "SENT 回写的 broker offset 必须与真实 Kafka record 一致");
        assertEquals(Integer.valueOf(2), integer("attempt", messageId), "替换 Worker 必须恰好重新 claim 一次");
        assertEquals(Long.valueOf(4L), longValue("version", messageId), "状态必须恰好经历 claim、release、claim、SENT 四次版本迁移");
        assertNull(stringValue("owner_token", messageId), "最终 SENT 必须清理 owner token");
        assertNull(timestamp("lease_until", messageId), "最终 SENT 必须清理 lease");

        recoveryWorker.stop();
    }

    @Test
    public void shouldNotReleaseLeaseAfterRealPublisherStartsAndDelayedAckCompletes() throws Exception {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-shutdown-after-publish-" + suffix;
        String messageId = "mock-message-shutdown-after-publish-" + suffix;
        createSharedTopic(topic);
        OutboxSaveResult saved = save(message(topic, key, messageId));
        assertNotNull(saved, "事务保存必须返回 Outbox 主键");

        DelayedAckKafkaPublisher delayedPublisher = new DelayedAckKafkaPublisher(publisher);
        publishingExecutor = executor("mock-shutdown-after-publish-");
        DefaultKafkaOutboxWorker publishingWorker = worker(publishingExecutor, delayedPublisher, 300L);
        publishingWorker.start();
        publishingWorker.scanOnce();
        assertTrue(delayedPublisher.awaitPublished(), "Worker 必须进入真实 publisher 调用");
        assertTrue(delayedPublisher.awaitBrokerAcknowledged(), "真实 broker ACK 必须先于受控回写完成");

        publishingWorker.stop();
        assertEquals(OutboxStatus.PROCESSING.getCode(), stringValue("status", messageId),
                "已进入 publisher 后停机不得提前释放租约");
        assertNotNull(stringValue("owner_token", messageId), "已进入 publisher 后停机不得清理 owner token");
        assertNotNull(timestamp("lease_until", messageId), "已进入 publisher 后停机不得清理 lease");
        assertNull(stringValue("last_error_code", messageId), "已进入 publisher 后停机不得写 shutdown release 错误");

        delayedPublisher.releaseAck();
        awaitStatus(messageId, OutboxStatus.SENT.getCode());
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        assertEquals(1, records.size(), "延迟 ACK 回写完成后必须且只能观察到一条真实 Kafka record");
        assertEquals(key, records.get(0).key(), "已开始发送的 Kafka key 不得变化");
        assertEquals(Integer.valueOf(0), Integer.valueOf(records.get(0).partition()), "单分区 topic 的已开始发送必须写入分区 0");
        assertEquals(Integer.valueOf(records.get(0).partition()), integer("broker_partition", messageId),
                "SENT 回写的 broker partition 必须与真实 Kafka record 一致");
        assertEquals(Long.valueOf(records.get(0).offset()), longValue("broker_offset", messageId),
                "SENT 回写的 broker offset 必须与真实 Kafka record 一致");
        assertEquals(Integer.valueOf(1), integer("attempt", messageId), "已开始发送后停机不得触发额外 claim");
        assertEquals(Long.valueOf(2L), longValue("version", messageId), "已开始发送路径只允许 claim 与 SENT 两次版本迁移");
        assertNull(stringValue("owner_token", messageId), "延迟 ACK SENT 回写后必须清理 owner token");
        assertNull(timestamp("lease_until", messageId), "延迟 ACK SENT 回写后必须清理 lease");
    }

    @Test
    public void shouldWaitForRealSynchronousPublisherCallBeforeStopping() throws Exception {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        String key = "mock-key-stop-during-synchronous-publish-" + suffix;
        String messageId = "mock-message-stop-during-synchronous-publish-" + suffix;
        createSharedTopic(topic);
        OutboxSaveResult saved = save(message(topic, key, messageId));
        assertNotNull(saved, "事务保存必须返回 Outbox 主键");

        BlockingReturnKafkaPublisher blockingPublisher = new BlockingReturnKafkaPublisher(publisher);
        publishingExecutor = executor("mock-stop-during-synchronous-publish-");
        DefaultKafkaOutboxWorker publishingWorker = worker(publishingExecutor, blockingPublisher, 5000L);
        CountDownLatch stopReturned = new CountDownLatch(1);
        Thread stopThread = new Thread(() -> {
            publishingWorker.stop();
            stopReturned.countDown();
        }, "mock-stop-during-synchronous-publish");
        publishingWorker.start();
        publishingWorker.scanOnce();
        assertTrue(blockingPublisher.awaitDelegateReturned(), "Worker 必须已进入真实 Kafka Publisher 同步调用");

        stopThread.start();
        assertFalse(stopReturned.await(100L, TimeUnit.MILLISECONDS),
                "真实 Kafka Publisher 同步调用尚未返回时 stop 不得返回");
        assertEquals(OutboxStatus.PROCESSING.getCode(), stringValue("status", messageId),
                "同步 publish 尚未返回时任务必须保持 PROCESSING 租约");
        assertNotNull(stringValue("owner_token", messageId), "同步 publish 尚未返回时不得清理 owner token");
        assertNotNull(timestamp("lease_until", messageId), "同步 publish 尚未返回时不得清理 lease");

        blockingPublisher.releaseReturn();
        stopThread.join(5000L);
        assertTrue(!stopThread.isAlive(), "同步 publish 返回后停机线程必须结束");
        awaitStatus(messageId, OutboxStatus.SENT.getCode());
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 1);
        assertEquals(1, records.size(), "同步 publish 停机边界必须且只能产生一条真实 Kafka record");
        assertEquals(key, records.get(0).key(), "同步 publish 停机边界不得改变 Kafka key");
        assertEquals(Integer.valueOf(0), Integer.valueOf(records.get(0).partition()),
                "单分区 topic 的同步 publish 必须写入分区 0");
        assertEquals(Integer.valueOf(records.get(0).partition()), integer("broker_partition", messageId),
                "SENT 回写的 broker partition 必须与同步 publish 的 Kafka record 一致");
        assertEquals(Long.valueOf(records.get(0).offset()), longValue("broker_offset", messageId),
                "SENT 回写的 broker offset 必须与同步 publish 的 Kafka record 一致");
        assertEquals(Integer.valueOf(1), integer("attempt", messageId), "同步 publish 停机不得触发额外 claim");
        assertEquals(Long.valueOf(2L), longValue("version", messageId), "同步 publish 停机路径只允许 claim 与 SENT 两次版本迁移");
        assertNull(stringValue("owner_token", messageId), "最终 SENT 必须清理 owner token");
        assertNull(timestamp("lease_until", messageId), "最终 SENT 必须清理 lease");
    }

    private OutboxSaveResult save(KafkaPublishMessage<String> message) {
        return transactionTemplate.execute(status -> outboxEngine.save(message));
    }

    private DefaultKafkaOutboxWorker worker(ThreadPoolTaskExecutor executor, KafkaPublisher workerPublisher,
                                            long shutdownAwaitMs) {
        SimpleKafkaOutboxProperties properties = new SimpleKafkaOutboxProperties();
        properties.getWorker().setConcurrency(1);
        properties.getWorker().setBatchSize(1);
        properties.getWorker().setScanIntervalMs(50L);
        properties.getWorker().setLeaseMs(5000L);
        properties.getWorker().setShutdownAwaitMs(shutdownAwaitMs);
        properties.getSend().setTimeoutMs(10000L);
        return new DefaultKafkaOutboxWorker(repository, serializer, retryPolicy, listener, traceScope, workerPublisher,
                properties, executor, Mockito.mock(TaskScheduler.class));
    }

    private ThreadPoolTaskExecutor blockingExecutor(String prefix, CountDownLatch taskEntered, CountDownLatch allowProcess) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(prefix);
        executor.setTaskDecorator(task -> () -> {
            taskEntered.countDown();
            try {
                if (!allowProcess.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待停机放行超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待停机放行被中断", e);
            }
            task.run();
        });
        executor.initialize();
        return executor;
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
                .messageType("mock.shutdown.recovery")
                .payload("mock-payload-" + key)
                .build();
    }

    private void awaitWorkerStopped(DefaultKafkaOutboxWorker worker) {
        long deadline = System.currentTimeMillis() + 5000L;
        while (worker.isRunning() && System.currentTimeMillis() < deadline) {
            sleep();
        }
        assertTrue(!worker.isRunning(), "stop 调用必须先让 Worker 停止领取");
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
        assertEquals(expectedStatus, status, "Outbox 状态必须在超时前到达预期值");
    }

    private Integer recordsInTopic(String topic, String key) {
        List<ConsumerRecord<String, String>> records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, Collections.singleton(key), 0);
        return records.size();
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

    private void shutdown(ThreadPoolTaskExecutor executor) {
        if (executor != null) {
            executor.shutdown();
        }
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "docs", "01_schema.sql");
    }

    private static final class BlockingReturnKafkaPublisher implements KafkaPublisher {
        private final KafkaPublisher delegate;
        private final CountDownLatch delegateReturned = new CountDownLatch(1);
        private final CountDownLatch allowReturn = new CountDownLatch(1);

        private BlockingReturnKafkaPublisher(KafkaPublisher delegate) {
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
            ListenableFuture<KafkaPublishResult> future = delegate.publish(message);
            delegateReturned.countDown();
            try {
                if (!allowReturn.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待同步 publish 返回放行超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待同步 publish 返回放行被中断", e);
            }
            return future;
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

        private boolean awaitDelegateReturned() throws InterruptedException {
            return delegateReturned.await(10L, TimeUnit.SECONDS);
        }

        private void releaseReturn() {
            allowReturn.countDown();
        }
    }

    private static final class DelayedAckKafkaPublisher implements KafkaPublisher {
        private final KafkaPublisher delegate;
        private final CountDownLatch published = new CountDownLatch(1);
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
            published.countDown();
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

        private boolean awaitPublished() throws InterruptedException {
            return published.await(5L, TimeUnit.SECONDS);
        }

        private boolean awaitBrokerAcknowledged() throws InterruptedException {
            return brokerAcknowledged.await(10L, TimeUnit.SECONDS);
        }

        private void releaseAck() {
            if (brokerAcknowledged.getCount() != 0L) {
                throw new IllegalStateException("真实 broker ACK 未完成，不能释放受控回写");
            }
            if (acknowledgedFailure != null) {
                delayed.setException(acknowledgedFailure);
            } else {
                delayed.set(acknowledgedResult);
            }
        }
    }
}
