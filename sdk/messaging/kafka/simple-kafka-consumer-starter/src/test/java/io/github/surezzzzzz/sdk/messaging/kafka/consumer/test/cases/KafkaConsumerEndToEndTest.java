package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ConsumerEventType;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerManager;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireResult;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyLease;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.KafkaConsumerEndToEndInitializer;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.KafkaConsumerEndToEndTestConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.SimpleKafkaConsumerTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.component.KafkaConsumerE2eHandlers;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support.*;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ContextConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Consumer 真实 Kafka 与 Redis 端到端测试。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleKafkaConsumerTestApplication.class)
@ContextConfiguration(initializers = KafkaConsumerEndToEndInitializer.class)
@Import(KafkaConsumerEndToEndTestConfiguration.class)
@Slf4j
public class KafkaConsumerEndToEndTest {

    private final List<String> currentMessageIds = new ArrayList<>();
    @Autowired
    private KafkaRouteTemplate kafkaRouteTemplate;
    @Autowired
    private SimpleRedisRouteRegistry redisRouteRegistry;
    @Autowired
    private KafkaConsumerContainerManager kafkaConsumerContainerManager;
    @Autowired
    private FailOnceDeadLetterPublisher deadLetterPublisher;
    @Autowired
    private KafkaConsumerIdempotencyChecker idempotencyChecker;

    @AfterEach
    public void clearCurrentMessageState() {
        for (String messageId : currentMessageIds) {
            KafkaConsumerE2eRecorder.clear(messageId);
            clearIdempotencyStates(messageId);
        }
        currentMessageIds.clear();
    }

    @Test
    public void testConsumesAcrossSingleAndThreeBrokerRouteDatasources() throws Exception {
        String v110MessageId = beginMessage();
        String v110Key = "v110-key-" + v110MessageId;
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V110, KafkaConsumerEndToEndHelper.TOPIC_V110,
                v110Key, "v110-value", v110MessageId);
        KafkaConsumerRecord<String, String> v110 = awaitOneRecord(v110MessageId);
        assertRecord(v110, v110MessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V110,
                KafkaConsumerEndToEndHelper.TOPIC_V110, v110Key, "v110-value", "consumeV110");
        assertConsumed(v110MessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V110,
                KafkaConsumerEndToEndHelper.TOPIC_V110, "consumeV110");

        String v28MessageId = beginMessage();
        String v28Key = "v28-key-" + v28MessageId;
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V28, KafkaConsumerEndToEndHelper.TOPIC_V28,
                v28Key, "v28-value", v28MessageId);
        KafkaConsumerRecord<String, String> v28 = awaitOneRecord(v28MessageId);
        assertRecord(v28, v28MessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V28,
                KafkaConsumerEndToEndHelper.TOPIC_V28, v28Key, "v28-value", "consumeV28");
        assertConsumed(v28MessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V28,
                KafkaConsumerEndToEndHelper.TOPIC_V28, "consumeV28");

        String v37MessageId = beginMessage();
        String v37Key = "v37-key-" + v37MessageId;
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_V37,
                v37Key, "v37-value", v37MessageId);
        KafkaConsumerRecord<String, String> v37 = awaitOneRecord(v37MessageId);
        assertRecord(v37, v37MessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                KafkaConsumerEndToEndHelper.TOPIC_V37, v37Key, "v37-value", "consumeV37");
        assertConsumed(v37MessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                KafkaConsumerEndToEndHelper.TOPIC_V37, "consumeV37");

        String clusterMessageId = beginMessage();
        String clusterKey = "cluster-key-" + clusterMessageId;
        send(KafkaConsumerEndToEndHelper.DATASOURCE_CLUSTER, KafkaConsumerEndToEndHelper.TOPIC_CLUSTER,
                clusterKey, "cluster-value", clusterMessageId);
        KafkaConsumerRecord<String, String> cluster = awaitOneRecord(clusterMessageId);
        assertRecord(cluster, clusterMessageId, KafkaConsumerEndToEndHelper.DATASOURCE_CLUSTER,
                KafkaConsumerEndToEndHelper.TOPIC_CLUSTER, clusterKey, "cluster-value", "consumeCluster");
        assertConsumed(clusterMessageId, KafkaConsumerEndToEndHelper.DATASOURCE_CLUSTER,
                KafkaConsumerEndToEndHelper.TOPIC_CLUSTER, "consumeCluster");
    }

    @Test
    public void testRefreshReplacesRealContainersAndContinuesConsumption() throws Exception {
        String beforeRefreshMessageId = beginMessage();
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_REFRESH,
                "refresh-before-" + beforeRefreshMessageId, "before-refresh", beforeRefreshMessageId);
        KafkaConsumerRecord<String, String> beforeRefresh = awaitOneRecord(beforeRefreshMessageId);
        log.info("刷新前实际消费消息：messageId={}，topic={}", beforeRefresh.getMessageId(), beforeRefresh.getTopic());
        assertRecord(beforeRefresh, beforeRefreshMessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                KafkaConsumerEndToEndHelper.TOPIC_REFRESH, "before-refresh");

        kafkaConsumerContainerManager.refresh();
        assertTrue(kafkaConsumerContainerManager.isRunning(), "刷新后 Consumer 容器管理器应保持运行");

        String afterRefreshMessageId = beginMessage();
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_REFRESH,
                "refresh-after-" + afterRefreshMessageId, "after-refresh", afterRefreshMessageId);
        KafkaConsumerRecord<String, String> afterRefresh = awaitOneRecord(afterRefreshMessageId);
        log.info("刷新后实际消费消息：messageId={}，topic={}，managerRunning={}", afterRefresh.getMessageId(),
                afterRefresh.getTopic(), kafkaConsumerContainerManager.isRunning());
        assertRecord(afterRefresh, afterRefreshMessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                KafkaConsumerEndToEndHelper.TOPIC_REFRESH, "after-refresh");
        assertConsumed(afterRefreshMessageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37);
    }

    @Test
    public void testSameTopicDifferentGroupsConsumeIndependently() throws Exception {
        String messageId = beginMessage();
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_SAME_TOPIC_GROUP,
                "same-topic-group-key-" + messageId, "same-topic-group", messageId);

        List<KafkaConsumerRecord<String, String>> records = KafkaConsumerE2eRecorder.awaitRecords(messageId, 2,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        List<KafkaConsumerEventContext> events = KafkaConsumerE2eRecorder.awaitEvents(messageId, 2,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        log.info("同 topic 不同 group 消费结果：records={}，events={}", records.size(), events.size());

        assertEquals(2, records.size(), "同一 source/topic 的两个独立消费组都必须进入业务 handler");
        assertEquals(2, events.size(), "两个独立消费组都必须产生成功消费事件");
        assertEquals(KafkaConsumerEndToEndHelper.TOPIC_SAME_TOPIC_GROUP, records.get(0).getTopic());
        assertEquals(KafkaConsumerEndToEndHelper.TOPIC_SAME_TOPIC_GROUP, records.get(1).getTopic());
        String groupARegistrationId = "kafkaConsumerE2eHandlers#" + KafkaConsumerE2eHandlers.class
                .getDeclaredMethod("consumeSameTopicGroupA", KafkaConsumerRecord.class).toGenericString();
        String groupBRegistrationId = "kafkaConsumerE2eHandlers#" + KafkaConsumerE2eHandlers.class
                .getDeclaredMethod("consumeSameTopicGroupB", KafkaConsumerRecord.class).toGenericString();
        assertTrue(records.stream().anyMatch(record -> groupARegistrationId.equals(record.getRegistrationId())),
                "group-a handler 必须收到消息");
        assertTrue(records.stream().anyMatch(record -> groupBRegistrationId.equals(record.getRegistrationId())),
                "group-b handler 必须收到消息");
        assertTrue(events.stream().allMatch(event -> ConsumerEventType.CONSUMED.equals(event.getEventType())),
                "两个独立消费组都必须成功消费，而非幂等拒绝");
    }

    @Test
    public void testRedisIdempotencyRejectsDuplicateAndRetainsCompletedMarker() throws Exception {
        String messageId = beginMessage();
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_IDEMPOTENCY,
                "idempotency-first-" + messageId, "first", messageId);
        KafkaConsumerRecord<String, String> consumed = awaitOneRecord(messageId);
        assertRecord(consumed, messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                KafkaConsumerEndToEndHelper.TOPIC_IDEMPOTENCY, "first");
        String key = idempotencyKey(messageId);
        assertCompletedIdempotencyMarker(key, "成功消费后 Redis 应保留 COMPLETED 幂等标记");

        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_IDEMPOTENCY,
                "idempotency-duplicate-" + messageId, "duplicate", messageId);
        List<KafkaConsumerEventContext> events = KafkaConsumerE2eRecorder.awaitEvents(messageId, 2,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        log.info("幂等重复结果：handlerRecordCount={}，eventTypes={}", KafkaConsumerE2eRecorder.records(messageId).size(),
                events.stream().map(KafkaConsumerEventContext::getEventType)
                        .collect(java.util.stream.Collectors.toList()));

        assertEquals(1, KafkaConsumerE2eRecorder.records(messageId).size(), "重复 messageId 不应再次进入业务 handler");
        assertEquals(2, events.size(), "首次消费与重复拒绝应各产生一个事件");
        assertEquals(ConsumerEventType.CONSUMED, events.get(0).getEventType());
        assertEquals(ConsumerEventType.IDEMPOTENT_REJECT, events.get(1).getEventType());
        assertCompletedIdempotencyMarker(key, "重复拒绝后完成标记不应被释放");
    }

    @Test
    public void testRetryableFailureRetriesThenPublishesDltWithOriginalHeaders() throws Exception {
        String messageId = beginMessage();
        String key = "retry-key-" + messageId;
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_RETRY,
                key, "retryable", messageId);
        List<KafkaConsumerRecord<String, String>> records = KafkaConsumerE2eRecorder.awaitRecords(messageId, 3,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        List<KafkaConsumerEventContext> events = KafkaConsumerE2eRecorder.awaitEvents(messageId, 3,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        ConsumerRecord<String, String> dlt = KafkaConsumerRawConsumer.consumeByMessageId(
                KafkaConsumerEndToEndHelper.BOOTSTRAP_V37, KafkaConsumerEndToEndHelper.TOPIC_RETRY + ".DLT",
                messageId, KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        log.info("可重试失败 DLT 结果：recordCount={}，eventTypes={}，dltPresent={}", records.size(),
                events.stream().map(KafkaConsumerEventContext::getEventType)
                        .collect(java.util.stream.Collectors.toList()), dlt != null);

        assertEquals(3, records.size(), "默认 maxAttempts=3 时可重试异常应精确调用 handler 三次");
        assertEquals(3, events.size(), "应产生两次 RETRY 与一次 DEAD_LETTER 事件");
        assertEquals(ConsumerEventType.RETRY, events.get(0).getEventType());
        assertEquals(1, events.get(0).getAttempt());
        assertEquals(ConsumerEventType.RETRY, events.get(1).getEventType());
        assertEquals(2, events.get(1).getAttempt());
        assertEquals(ConsumerEventType.DEAD_LETTER, events.get(2).getEventType());
        assertEquals(3, events.get(2).getAttempt());
        assertEquals(ErrorCode.CONSUME_RETRYABLE, events.get(2).getErrorCode());
        assertNotNull(dlt, "持续失败消息应真实投递至 DLT");
        assertEquals(key, dlt.key(), "DLT key 应保持原样");
        assertEquals("retryable", dlt.value(), "DLT value 应保持原样");
        assertEquals(messageId, KafkaConsumerHeaderHelper.headerText(dlt,
                SimpleKafkaConsumerConstant.HEADER_MESSAGE_ID));
        assertEquals("mock-custom-header", KafkaConsumerHeaderHelper.headerText(dlt, "mock-custom-header"));
        assertEquals(KafkaConsumerEndToEndHelper.TOPIC_RETRY, KafkaConsumerHeaderHelper.headerText(dlt,
                SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ORIGINAL_TOPIC));
        assertEquals(ErrorCode.CONSUME_RETRYABLE, KafkaConsumerHeaderHelper.headerText(dlt,
                SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ERROR_CODE));
        assertEquals("3", KafkaConsumerHeaderHelper.headerText(dlt,
                SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ATTEMPT));
        assertCompletedIdempotencyMarker(idempotencyKey(messageId,
                        KafkaConsumerEndToEndHelper.DATASOURCE_V37, "mock-consumer-retry-e2e"),
                "终态 DLT 后 Redis 应保留 COMPLETED 幂等标记");
    }

    @Test
    public void testDeadLetterFailureStopsContainerThenRefreshReplaysAndPublishesOnce() throws Exception {
        String messageId = beginMessage();
        deadLetterPublisher.failFirstPublish(messageId);
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_DEAD_LETTER_RECOVERY,
                "dead-letter-recovery-key-" + messageId, "dead-letter-recovery", messageId);

        List<KafkaConsumerRecord<String, String>> firstAttemptRecords = KafkaConsumerE2eRecorder.awaitRecords(messageId,
                1, KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        List<KafkaConsumerEventContext> firstAttemptEvents = KafkaConsumerE2eRecorder.awaitEvents(messageId, 1,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        log.info("DLT 首次失败：records={}，events={}，failedCount={}", firstAttemptRecords.size(),
                firstAttemptEvents.size(), deadLetterPublisher.getFailedCount());

        assertEquals(1, firstAttemptRecords.size(), "首次失败前 handler 应只执行一次");
        assertEquals(1, firstAttemptEvents.size(), "首次 DLT 失败应只产生 ERROR 事件");
        assertEquals(ConsumerEventType.ERROR, firstAttemptEvents.get(0).getEventType());
        assertEquals(ErrorCode.DEAD_LETTER_PUBLISH_FAILED, firstAttemptEvents.get(0).getErrorCode());
        assertEquals(1, deadLetterPublisher.getFailedCount(), "专用 DLT 投递器必须只注入一次失败");
        assertFalse(Boolean.TRUE.equals(redis().hasKey(idempotencyKey(messageId,
                        KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                        "mock-consumer-dead-letter-recovery-e2e"))),
                "DLT 失败后必须释放当前 Redis 处理租约，允许刷新后的 Kafka 重投");
        assertNull(KafkaConsumerRawConsumer.consumeByMessageId(KafkaConsumerEndToEndHelper.BOOTSTRAP_V37,
                        KafkaConsumerEndToEndHelper.TOPIC_DEAD_LETTER_RECOVERY + ".DLT", messageId, 1000L),
                "首次 DLT 投递失败不得生成死信消息");

        kafkaConsumerContainerManager.refresh();
        assertTrue(kafkaConsumerContainerManager.isRunning(), "DLT 失败后的刷新必须重建并启动 Consumer 容器");

        List<KafkaConsumerRecord<String, String>> replayRecords = KafkaConsumerE2eRecorder.awaitRecords(messageId, 2,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        List<KafkaConsumerEventContext> replayEvents = KafkaConsumerE2eRecorder.awaitEvents(messageId, 2,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        ConsumerRecord<String, String> dlt = KafkaConsumerRawConsumer.consumeByMessageId(
                KafkaConsumerEndToEndHelper.BOOTSTRAP_V37,
                KafkaConsumerEndToEndHelper.TOPIC_DEAD_LETTER_RECOVERY + ".DLT", messageId,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        log.info("DLT 刷新重投：records={}，events={}，dltPresent={}", replayRecords.size(), replayEvents.size(),
                dlt != null);

        assertEquals(2, replayRecords.size(), "刷新后同一源消息必须重新进入 handler，且不得被跳过");
        assertEquals(2, replayEvents.size(), "首次失败与重投成功应各产生一个终态事件");
        assertEquals(ConsumerEventType.ERROR, replayEvents.get(0).getEventType());
        assertEquals(ConsumerEventType.DEAD_LETTER, replayEvents.get(1).getEventType());
        assertEquals(ErrorCode.CONSUME_FATAL, replayEvents.get(1).getErrorCode());
        assertNotNull(dlt, "刷新重投后 DLT 必须成功写入");
        assertEquals("1", KafkaConsumerHeaderHelper.headerText(dlt,
                SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ATTEMPT));
        assertCompletedIdempotencyMarker(idempotencyKey(messageId,
                        KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                        "mock-consumer-dead-letter-recovery-e2e"),
                "最终 DLT 成功后 Redis 必须保留 COMPLETED 幂等标记");
    }

    @Test
    public void testProcessingLeaseDoesNotAcknowledgeThenReplaysAfterLeaseExpiry() throws Exception {
        String messageId = beginMessage();
        String groupId = "mock-consumer-processing-lease-e2e";
        String key = idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId);
        redis().opsForValue().set(key, SimpleKafkaConsumerConstant.IDEMPOTENCY_PROCESSING_VALUE_PREFIX
                + "mock-stale-owner", 5000L, TimeUnit.MILLISECONDS);
        Long committedBeforeSend = committedOffset(groupId, KafkaConsumerEndToEndHelper.TOPIC_PROCESSING_LEASE, 0);
        log.info("预置处理中租约：key={}，messageId={}，committedBeforeSend={}", key, messageId, committedBeforeSend);

        SendResult<String, String> sent = send(KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                KafkaConsumerEndToEndHelper.TOPIC_PROCESSING_LEASE, "processing-lease-key-" + messageId,
                "processing-lease", messageId);
        long sourceOffset = sent.getRecordMetadata().offset();

        assertTrue(KafkaConsumerE2eRecorder.awaitIdempotencyStatus(messageId,
                        KafkaConsumerIdempotencyAcquireStatus.IN_PROGRESS,
                        KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS),
                "目标 source record 必须实际进入 Redis IN_PROGRESS 幂等分支");
        assertTrue(KafkaConsumerE2eRecorder.awaitNoCallbacks(messageId, 250L),
                "确认 IN_PROGRESS 后不得进入 Handler、DLT 或重复拒绝事件");
        assertEquals(SimpleKafkaConsumerConstant.IDEMPOTENCY_PROCESSING_VALUE_PREFIX + "mock-stale-owner",
                redis().opsForValue().get(key), "处理中租约不得被非 owner 改写或删除");
        assertEquals(committedBeforeSend, committedOffset(groupId, KafkaConsumerEndToEndHelper.TOPIC_PROCESSING_LEASE, 0),
                "处理中租约不得提交 source offset");

        awaitRedisKeyAbsent(key, KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        kafkaConsumerContainerManager.refresh();
        assertTrue(kafkaConsumerContainerManager.isRunning(), "租约到期后的刷新必须重建 Consumer 容器");

        KafkaConsumerRecord<String, String> replayed = awaitOneRecord(messageId);
        assertRecord(replayed, messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                KafkaConsumerEndToEndHelper.TOPIC_PROCESSING_LEASE, "processing-lease-key-" + messageId,
                "processing-lease", "consumeProcessingLease");
        assertConsumed(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                KafkaConsumerEndToEndHelper.TOPIC_PROCESSING_LEASE, "consumeProcessingLease");
        assertCompletedIdempotencyMarker(key, "租约到期后的重投成功必须写入 COMPLETED 标记");
        assertEquals(sourceOffset + 1L, committedOffset(groupId, KafkaConsumerEndToEndHelper.TOPIC_PROCESSING_LEASE, 0),
                "租约到期后的重投成功才可提交 source offset");
    }

    @Test
    public void testExpiredOwnerCannotReleaseNewLeaseOrCompletedMarker() throws Exception {
        String messageId = beginMessage();
        String groupId = "mock-consumer-owner-safety-e2e";
        String key = idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId);

        KafkaConsumerIdempotencyAcquireResult first = idempotencyChecker.acquire(messageId,
                KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId);
        assertEquals(KafkaConsumerIdempotencyAcquireStatus.ACQUIRED, first.getStatus(), "首次领取必须获得处理租约");
        KafkaConsumerIdempotencyLease oldLease = first.getLease();
        assertNotNull(oldLease, "首次领取必须携带 owner 租约");
        awaitRedisKeyAbsent(key, KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);

        KafkaConsumerIdempotencyAcquireResult second = idempotencyChecker.acquire(messageId,
                KafkaConsumerEndToEndHelper.DATASOURCE_V37, groupId);
        assertEquals(KafkaConsumerIdempotencyAcquireStatus.ACQUIRED, second.getStatus(), "过期后新 owner 必须重新领取租约");
        KafkaConsumerIdempotencyLease newLease = second.getLease();
        assertNotNull(newLease, "新 owner 必须携带处理租约");
        assertFalse(oldLease.release(), "迟到旧 owner 不得删除新 owner 的处理中租约");
        assertTrue(redis().opsForValue().get(key).startsWith(
                SimpleKafkaConsumerConstant.IDEMPOTENCY_PROCESSING_VALUE_PREFIX), "新 owner 处理中租约必须仍然存在");

        assertTrue(newLease.complete(), "新 owner 必须能写入完成标记");
        assertFalse(oldLease.release(), "迟到旧 owner 不得删除新 owner 写入的完成标记");
        assertCompletedIdempotencyMarker(key, "旧 owner 的迟到释放不得删除完成标记");
    }

    @Test
    public void testFatalFailurePublishesDltWithoutRetry() throws Exception {
        String messageId = beginMessage();
        send(KafkaConsumerEndToEndHelper.DATASOURCE_V37, KafkaConsumerEndToEndHelper.TOPIC_RETRY,
                "fatal-key-" + messageId, "fatal", messageId);
        List<KafkaConsumerRecord<String, String>> records = KafkaConsumerE2eRecorder.awaitRecords(messageId, 1,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        List<KafkaConsumerEventContext> events = KafkaConsumerE2eRecorder.awaitEvents(messageId, 1,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        ConsumerRecord<String, String> dlt = KafkaConsumerRawConsumer.consumeByMessageId(
                KafkaConsumerEndToEndHelper.BOOTSTRAP_V37, KafkaConsumerEndToEndHelper.TOPIC_RETRY + ".DLT",
                messageId, KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);

        assertEquals(1, records.size(), "fatal 异常不应进入重试循环");
        assertEquals(1, events.size(), "fatal 异常只应产生 DEAD_LETTER 事件");
        assertEquals(ConsumerEventType.DEAD_LETTER, events.get(0).getEventType());
        assertEquals(1, events.get(0).getAttempt());
        assertEquals(ErrorCode.CONSUME_FATAL, events.get(0).getErrorCode());
        assertNotNull(dlt, "fatal 异常应真实投递至 DLT");
        assertEquals("1", KafkaConsumerHeaderHelper.headerText(dlt,
                SimpleKafkaConsumerConstant.DEAD_LETTER_HEADER_ATTEMPT));
        assertCompletedIdempotencyMarker(idempotencyKey(messageId,
                        KafkaConsumerEndToEndHelper.DATASOURCE_V37, "mock-consumer-retry-e2e"),
                "fatal DLT 后 Redis 应保留 COMPLETED 幂等标记");
    }

    private String beginMessage() {
        String messageId = KafkaConsumerEndToEndHelper.messageId();
        currentMessageIds.add(messageId);
        KafkaConsumerE2eRecorder.clear(messageId);
        clearIdempotencyStates(messageId);
        return messageId;
    }

    private void clearIdempotencyStates(String messageId) {
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V110,
                "mock-consumer-v110-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V28,
                "mock-consumer-v28-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-v37-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_CLUSTER,
                "mock-consumer-cluster-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-idempotency-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-dead-letter-recovery-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-group-a-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-group-b-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-retry-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-refresh-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-processing-lease-e2e"));
        redis().delete(idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-owner-safety-e2e"));
    }

    private SendResult<String, String> send(String datasourceKey, String topic, String key, String value,
                                            String messageId) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        record.headers().add(new RecordHeader(SimpleKafkaConsumerConstant.HEADER_MESSAGE_ID,
                messageId.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("mock-custom-header",
                "mock-custom-header".getBytes(StandardCharsets.UTF_8)));
        return kafkaRouteTemplate.sendOn(datasourceKey, record).get(30, TimeUnit.SECONDS);
    }

    private KafkaConsumerRecord<String, String> awaitOneRecord(String messageId) {
        List<KafkaConsumerRecord<String, String>> records = KafkaConsumerE2eRecorder.awaitRecords(messageId, 1,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        assertEquals(1, records.size(), "应精确消费一次 messageId=" + messageId);
        return records.get(0);
    }

    private void assertRecord(KafkaConsumerRecord<String, String> record, String messageId,
                              String datasourceKey, String topic, String value) {
        log.info("Consumer record：messageId={}，datasource={}，topic={}，key={}，value={}，registrationId={}",
                record.getMessageId(), record.getDatasourceKey(), record.getTopic(), record.getKey(),
                record.getValue(), record.getRegistrationId());
        assertEquals(messageId, record.getMessageId());
        assertEquals(datasourceKey, record.getDatasourceKey());
        assertEquals(topic, record.getTopic());
        assertEquals(value, record.getValue());
        assertNotNull(record.getAcknowledgment(), "手动提交分支应向 handler 暴露 acknowledgment");
    }

    private void assertRecord(KafkaConsumerRecord<String, String> record, String messageId,
                              String datasourceKey, String topic, String key, String value,
                              String handlerMethodName) {
        assertRecord(record, messageId, datasourceKey, topic, value);
        assertEquals(key, record.getKey());
        assertEquals(expectedRegistrationId(handlerMethodName), record.getRegistrationId(),
                "消费记录必须由预期 handler 注册项处理");
    }

    private void assertConsumed(String messageId, String datasourceKey) {
        List<KafkaConsumerEventContext> events = KafkaConsumerE2eRecorder.awaitEvents(messageId, 1,
                KafkaConsumerEndToEndHelper.WAIT_TIMEOUT_MS);
        assertEquals(1, events.size());
        KafkaConsumerEventContext event = events.get(0);
        log.info("Consumer event：messageId={}，type={}，attempt={}，datasource={}，topic={}，registrationId={}",
                event.getMessageId(), event.getEventType(), event.getAttempt(), event.getDatasourceKey(),
                event.getTopic(), event.getRegistrationId());
        assertEquals(ConsumerEventType.CONSUMED, event.getEventType());
        assertEquals(1, events.get(0).getAttempt());
        assertEquals(datasourceKey, events.get(0).getDatasourceKey());
    }

    private void assertConsumed(String messageId, String datasourceKey, String topic, String handlerMethodName) {
        assertConsumed(messageId, datasourceKey);
        KafkaConsumerEventContext event = KafkaConsumerE2eRecorder.events(messageId).get(0);
        assertEquals(topic, event.getTopic());
        assertEquals(expectedRegistrationId(handlerMethodName), event.getRegistrationId(),
                "消费事件必须由预期 handler 注册项产生");
    }

    private String expectedRegistrationId(String handlerMethodName) {
        for (java.lang.reflect.Method method : KafkaConsumerE2eHandlers.class.getDeclaredMethods()) {
            if (handlerMethodName.equals(method.getName())) {
                return "kafkaConsumerE2eHandlers#" + method.toGenericString();
            }
        }
        throw new IllegalArgumentException("未找到 E2E handler 方法：" + handlerMethodName);
    }

    private StringRedisTemplate redis() {
        return redisRouteRegistry.getStringRedisTemplate("e2e");
    }

    private void assertCompletedIdempotencyMarker(String key, String message) {
        String value = redis().opsForValue().get(key);
        Long expireMs = redis().getExpire(key, TimeUnit.MILLISECONDS);
        log.info("Redis 幂等终态：key={}，value={}，expireMs={}", key, value, expireMs);
        assertEquals(SimpleKafkaConsumerConstant.IDEMPOTENCY_COMPLETED_VALUE, value, message);
        assertNotNull(expireMs, "完成标记必须存在过期时间");
        assertTrue(expireMs > 0L, "完成标记必须保留正剩余 TTL");
    }

    private void awaitRedisKeyAbsent(String key, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (Boolean.TRUE.equals(redis().hasKey(key)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        assertFalse(Boolean.TRUE.equals(redis().hasKey(key)), "处理租约必须在超时窗口内失效：key=" + key);
    }

    private Long committedOffset(String groupId, String topic, int partition) throws Exception {
        Properties properties = new Properties();
        properties.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, KafkaConsumerEndToEndHelper.BOOTSTRAP_V37);
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        try (AdminClient client = AdminClient.create(properties)) {
            Map<TopicPartition, OffsetAndMetadata> offsets = client.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata().get(30L, TimeUnit.SECONDS);
            OffsetAndMetadata offset = offsets.get(topicPartition);
            Long committed = offset == null ? null : offset.offset();
            log.info("Kafka 消费组提交位点：groupId={}，topic={}，partition={}，offset={}", groupId, topic, partition,
                    committed);
            return committed;
        }
    }

    private String idempotencyKey(String messageId) {
        return idempotencyKey(messageId, KafkaConsumerEndToEndHelper.DATASOURCE_V37,
                "mock-consumer-idempotency-e2e");
    }

    private String idempotencyKey(String messageId, String datasourceKey, String groupId) {
        String scopedMessageId = String.format("%d:%s:%d:%s:%s", datasourceKey.length(), datasourceKey,
                groupId.length(), groupId, messageId);
        return String.format(SimpleKafkaConsumerConstant.IDEMPOTENCY_REDIS_KEY_TEMPLATE, scopedMessageId);
    }
}
