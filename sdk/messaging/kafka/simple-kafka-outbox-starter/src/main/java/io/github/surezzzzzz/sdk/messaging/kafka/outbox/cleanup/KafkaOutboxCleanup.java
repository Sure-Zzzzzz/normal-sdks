package io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupBatchResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.sql.Timestamp;
import java.util.concurrent.ScheduledFuture;

/**
 * Kafka Outbox SENT 记录清理任务
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaOutboxCleanup {

    private final KafkaOutboxRepository repository;
    private final KafkaOutboxEventListener listener;
    private final SimpleKafkaOutboxProperties properties;
    private final TaskScheduler scheduler;
    private volatile ScheduledFuture<?> future;

    /**
     * 创建清理任务
     *
     * @param repository Repository
     * @param listener   Listener
     * @param properties 配置
     * @param scheduler  调度器
     */
    public KafkaOutboxCleanup(KafkaOutboxRepository repository, KafkaOutboxEventListener listener,
                              SimpleKafkaOutboxProperties properties, TaskScheduler scheduler) {
        this.repository = repository;
        this.listener = listener;
        this.properties = properties;
        this.scheduler = scheduler;
    }

    /**
     * 启动固定间隔清理任务。
     */
    public void start() {
        future = scheduler.scheduleWithFixedDelay(this::cleanupOnce, properties.getCleanup().getIntervalMs());
    }

    /**
     * 停止清理任务。
     */
    public void stop() {
        ScheduledFuture<?> scheduled = future;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }

    /**
     * 执行一次完整 keyset 分批清理。
     */
    public void cleanupOnce() {
        try {
            Timestamp expireBefore = repository.resolveExpireBefore(properties.getCleanup().getRetentionDays());
            Timestamp lastSentAt = null;
            Long lastId = null;
            while (true) {
                OutboxCleanupBatchResult result = repository.cleanupBatch(expireBefore, lastSentAt, lastId,
                        properties.getCleanup().getBatchSize());
                if (result.getCandidateCount() == 0) {
                    return;
                }
                notifyCleanup(new OutboxCleanupContext(result.getDeletedCount(), expireBefore));
                lastSentAt = result.getLastSentAt();
                lastId = result.getLastId();
                if (result.getCandidateCount() < properties.getCleanup().getBatchSize()) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Kafka Outbox 清理失败，errorCode={}", ErrorCode.KAFKA_OUTBOX_006);
        }
    }

    /**
     * 通知单批清理结果，Listener 异常仅告警不影响清理。
     */
    private void notifyCleanup(OutboxCleanupContext context) {
        try {
            listener.onCleanup(context);
        } catch (RuntimeException e) {
            log.warn("Kafka Outbox Listener onCleanup 执行失败，deletedCount={}", context.getDeletedCount());
        }
    }
}
