package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupBatchResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 首次 SENT 回写失败的测试 Repository
 *
 * @author surezzzzzz
 */
public class FirstMarkSentFailureKafkaOutboxRepository implements KafkaOutboxRepository {

    private final KafkaOutboxRepository delegate;
    private final long targetRecordId;
    private final AtomicBoolean failed = new AtomicBoolean(false);

    public FirstMarkSentFailureKafkaOutboxRepository(KafkaOutboxRepository delegate, long targetRecordId) {
        this.delegate = delegate;
        this.targetRecordId = targetRecordId;
    }

    @Override
    public Long save(OutboxRecordEntity record) {
        return delegate.save(record);
    }

    @Override
    public List<OutboxRecordEntity> claim(int candidateLimit, long leaseMicros) {
        return delegate.claim(candidateLimit, leaseMicros);
    }

    @Override
    public boolean markSent(OutboxRecordEntity record) {
        if (record.getId() == targetRecordId && failed.compareAndSet(false, true)) {
            throw new IllegalStateException("mock markSent persistence failure");
        }
        return delegate.markSent(record);
    }

    @Override
    public boolean markRetry(OutboxRecordEntity record, long delayMicros, String errorCode, String errorSummary) {
        return delegate.markRetry(record, delayMicros, errorCode, errorSummary);
    }

    @Override
    public boolean markPoison(OutboxRecordEntity record, String errorCode, String errorSummary) {
        return delegate.markPoison(record, errorCode, errorSummary);
    }

    @Override
    public boolean releaseBeforeSend(OutboxRecordEntity record, String errorCode, String errorSummary) {
        return delegate.releaseBeforeSend(record, errorCode, errorSummary);
    }

    @Override
    public Timestamp resolveExpireBefore(int retentionDays) {
        return delegate.resolveExpireBefore(retentionDays);
    }

    @Override
    public OutboxCleanupBatchResult cleanupBatch(Timestamp expireBefore, Timestamp lastSentAt, Long lastId,
                                                 int batchSize) {
        return delegate.cleanupBatch(expireBefore, lastSentAt, lastId, batchSize);
    }

    public boolean hasFailed() {
        return failed.get();
    }
}
