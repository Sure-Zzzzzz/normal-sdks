package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Consumer 端到端测试事件与回调记录器。
 *
 * @author surezzzzzz
 */
public final class KafkaConsumerE2eRecorder {

    private static final List<KafkaConsumerRecord<String, String>> RECORDS = new ArrayList<>();
    private static final List<KafkaConsumerEventContext> EVENTS = new ArrayList<>();
    private static final Map<String, KafkaConsumerIdempotencyAcquireStatus> IDEMPOTENCY_STATUSES = new HashMap<>();

    private KafkaConsumerE2eRecorder() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static synchronized void record(KafkaConsumerRecord<String, String> record) {
        RECORDS.add(record);
        KafkaConsumerE2eRecorder.class.notifyAll();
    }

    public static synchronized void event(KafkaConsumerEventContext context) {
        EVENTS.add(context);
        KafkaConsumerE2eRecorder.class.notifyAll();
    }

    public static synchronized void recordIdempotencyStatus(String messageId,
                                                            KafkaConsumerIdempotencyAcquireStatus status) {
        IDEMPOTENCY_STATUSES.put(messageId, status);
        KafkaConsumerE2eRecorder.class.notifyAll();
    }

    public static synchronized boolean awaitIdempotencyStatus(String messageId,
                                                              KafkaConsumerIdempotencyAcquireStatus expected,
                                                              long timeoutMs) {
        await(timeoutMs, () -> expected.equals(IDEMPOTENCY_STATUSES.get(messageId)));
        return expected.equals(IDEMPOTENCY_STATUSES.get(messageId));
    }

    public static synchronized List<KafkaConsumerRecord<String, String>> awaitRecords(
            String messageId, int expectedCount, long timeoutMs) {
        await(timeoutMs, () -> countRecords(messageId) >= expectedCount);
        return records(messageId);
    }

    public static synchronized List<KafkaConsumerEventContext> awaitEvents(
            String messageId, int expectedCount, long timeoutMs) {
        await(timeoutMs, () -> countEvents(messageId) >= expectedCount);
        return events(messageId);
    }

    public static synchronized List<KafkaConsumerRecord<String, String>> records(String messageId) {
        List<KafkaConsumerRecord<String, String>> result = new ArrayList<>();
        for (KafkaConsumerRecord<String, String> record : RECORDS) {
            if (messageId.equals(record.getMessageId())) {
                result.add(record);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized List<KafkaConsumerEventContext> events(String messageId) {
        List<KafkaConsumerEventContext> result = new ArrayList<>();
        for (KafkaConsumerEventContext event : EVENTS) {
            if (messageId.equals(event.getMessageId())) {
                result.add(event);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized void clear(String messageId) {
        RECORDS.removeIf(record -> messageId.equals(record.getMessageId()));
        EVENTS.removeIf(event -> messageId.equals(event.getMessageId()));
        IDEMPOTENCY_STATUSES.remove(messageId);
    }

    /**
     * 等待一段时间并确认目标消息没有业务或事件回调。
     *
     * @param messageId     消息 id
     * @param quietPeriodMs 静默等待时长（毫秒）
     * @return true 表示等待期间未出现回调
     */
    public static synchronized boolean awaitNoCallbacks(String messageId, long quietPeriodMs) {
        long deadline = System.currentTimeMillis() + quietPeriodMs;
        while (countRecords(messageId) == 0 && countEvents(messageId) == 0
                && System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            try {
                TimeUnit.MILLISECONDS.timedWait(KafkaConsumerE2eRecorder.class, remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Consumer 端到端回调被中断", e);
            }
        }
        return countRecords(messageId) == 0 && countEvents(messageId) == 0;
    }

    private static void await(long timeoutMs, Condition condition) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.matches() && System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            try {
                TimeUnit.MILLISECONDS.timedWait(KafkaConsumerE2eRecorder.class, remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Consumer 端到端回调被中断", e);
            }
        }
    }

    private static int countRecords(String messageId) {
        return records(messageId).size();
    }

    private static int countEvents(String messageId) {
        return events(messageId).size();
    }

    private interface Condition {

        boolean matches();
    }
}
