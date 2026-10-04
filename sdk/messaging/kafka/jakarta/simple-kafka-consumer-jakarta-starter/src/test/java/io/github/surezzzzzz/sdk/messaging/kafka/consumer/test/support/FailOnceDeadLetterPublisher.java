package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 首次死信投递失败的端到端测试投递器。
 *
 * @author surezzzzzz
 */
public class FailOnceDeadLetterPublisher implements DeadLetterPublisher {

    private final DeadLetterPublisher delegate;
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private final AtomicInteger failedCount = new AtomicInteger();
    private volatile String messageId;

    public FailOnceDeadLetterPublisher(DeadLetterPublisher delegate) {
        this.delegate = delegate;
    }

    public void failFirstPublish(String messageId) {
        this.messageId = messageId;
        failed.set(false);
        failedCount.set(0);
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    @Override
    public boolean publish(KafkaConsumerRecord<?, ?> record, Exception cause, int attempt, String errorCode) {
        if (messageId != null && messageId.equals(record.getMessageId()) && failed.compareAndSet(false, true)) {
            failedCount.incrementAndGet();
            return false;
        }
        return delegate.publish(record, cause, attempt, errorCode);
    }
}
