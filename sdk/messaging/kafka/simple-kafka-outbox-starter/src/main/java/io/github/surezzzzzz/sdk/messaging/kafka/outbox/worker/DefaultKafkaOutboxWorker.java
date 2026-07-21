package io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRetryContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.KafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.support.KafkaOutboxStringHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceScope;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认 Kafka Outbox Worker
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaOutboxWorker implements KafkaOutboxWorker, SmartLifecycle {

    private final KafkaOutboxRepository repository;
    private final KafkaOutboxMessageSerializer serializer;
    private final KafkaOutboxRetryPolicy retryPolicy;
    private final KafkaOutboxEventListener listener;
    private final KafkaOutboxTraceScope traceScope;
    private final KafkaPublisher publisher;
    private final SimpleKafkaOutboxProperties properties;
    private final ThreadPoolTaskExecutor executor;
    private final TaskScheduler scheduler;
    /**
     * 并发槽位信号量，领取数不超过空闲槽位，执行器零排队
     */
    private final Semaphore slots;
    /**
     * 发布门面锁：原子化“当前代次检查 + 同步 publisher.publish()”，stop 在此等待同步发布完成
     */
    private final Object publisherGate = new Object();
    /**
     * 是否运行中，stop 在 publisherGate 内置 false
     */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * 生命周期代次，每次 start 递增，绑定任务到具体 start 调用
     */
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    /**
     * 当前自调度扫描任务句柄，stop 时取消，每轮调度后持续更新
     */
    private volatile ScheduledFuture<?> scanFuture;

    /**
     * 创建默认 Worker
     *
     * @param repository  Repository
     * @param serializer  快照序列化器
     * @param retryPolicy 重试策略
     * @param listener    Listener
     * @param traceScope  traceId 作用域
     * @param publisher   Publisher
     * @param properties  配置
     * @param executor    零排队执行器
     * @param scheduler   调度器
     */
    public DefaultKafkaOutboxWorker(KafkaOutboxRepository repository,
                                    KafkaOutboxMessageSerializer serializer,
                                    KafkaOutboxRetryPolicy retryPolicy,
                                    KafkaOutboxEventListener listener,
                                    KafkaOutboxTraceScope traceScope,
                                    KafkaPublisher publisher,
                                    SimpleKafkaOutboxProperties properties,
                                    ThreadPoolTaskExecutor executor,
                                    TaskScheduler scheduler) {
        this.repository = repository;
        this.serializer = serializer;
        this.retryPolicy = retryPolicy;
        this.listener = listener;
        this.traceScope = traceScope;
        this.publisher = publisher;
        this.properties = properties;
        this.executor = executor;
        this.scheduler = scheduler;
        this.slots = new Semaphore(properties.getWorker().getConcurrency());
    }

    /**
     * 触发一次领取扫描
     */
    @Override
    public void scanOnce() {
        doScanOnce();
    }

    /**
     * 执行一次候选领取，返回值决定下次调度间隔：true 用 scan-interval-ms，false 用 idle-interval-ms。
     * 只有真正无候选（空轮）才返回 false；忙碌、异常等情况返回 true 维持短间隔。
     */
    private boolean doScanOnce() {
        if (!running.get()) {
            // 停止中，scheduledScan 后续会检查 running 不再调度，返回值无实际影响
            return true;
        }
        int candidateLimit = Math.min(properties.getWorker().getBatchSize(), slots.availablePermits());
        if (candidateLimit <= SimpleKafkaOutboxConstant.ZERO) {
            // 槽位满，Worker 正在忙，短间隔轮询等待槽位释放
            return true;
        }
        long generation = lifecycleGeneration.get();
        List<OutboxRecordEntity> claimed;
        try {
            claimed = repository.claim(candidateLimit,
                    properties.getWorker().getLeaseMs() * SimpleKafkaOutboxConstant.MILLIS_TO_MICROS);
        } catch (RuntimeException e) {
            log.warn("Kafka Outbox 领取失败，errorCode={}", ErrorCode.KAFKA_OUTBOX_006);
            // 领取异常，短间隔重试
            return true;
        }
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        for (OutboxRecordEntity record : claimed) {
            if (!slots.tryAcquire()) {
                releaseBeforeSend(record);
                continue;
            }
            notifyListener(EventType.CLAIMED, toEvent(record, OutboxStatus.PROCESSING, null));
            try {
                executor.execute(() -> process(record, generation));
            } catch (RejectedExecutionException e) {
                slots.release();
                releaseBeforeSend(record);
            }
        }
        return true;
    }

    /**
     * 启动 Worker。
     */
    @Override
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            long gen = lifecycleGeneration.incrementAndGet();
            try {
                scanFuture = scheduler.schedule(() -> scheduledScan(gen), new Date());
            } catch (RuntimeException e) {
                running.set(false);
                throw e;
            }
        }
    }

    /**
     * 自调度扫描：有候选用 scan-interval-ms，空轮用 idle-interval-ms，停止后不再重调度。
     */
    private void scheduledScan(long generation) {
        if (!running.get() || lifecycleGeneration.get() != generation) {
            return;
        }
        boolean hadCandidates = doScanOnce();
        if (!running.get() || lifecycleGeneration.get() != generation) {
            return;
        }
        long delay = hadCandidates
                ? properties.getWorker().getScanIntervalMs()
                : properties.getWorker().getIdleIntervalMs();
        scanFuture = scheduler.schedule(() -> scheduledScan(generation),
                new Date(System.currentTimeMillis() + delay));
    }

    /**
     * 停止 Worker。
     */
    @Override
    public synchronized void stop() {
        synchronized (publisherGate) {
            running.set(false);
        }
        ScheduledFuture<?> future = scanFuture;
        if (future != null) {
            future.cancel(false);
        }
        long deadline = System.currentTimeMillis() + properties.getWorker().getShutdownAwaitMs();
        while (slots.availablePermits() < properties.getWorker().getConcurrency()
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(Math.min(properties.getWorker().getScanIntervalMs(),
                        properties.getWorker().getShutdownAwaitMs()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 停止 Worker 并执行回调。
     */
    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    /**
     * @return 是否运行中
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * @return 自动启动
     */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * @return 生命周期阶段
     */
    @Override
    public int getPhase() {
        return SimpleKafkaOutboxConstant.SMART_LIFECYCLE_PHASE;
    }

    /**
     * 处理单条已领取记录：反序列化、开 trace、发布、等待结果并回写状态。
     */
    private void process(OutboxRecordEntity record, long generation) {
        try {
            if (!isActive(generation)) {
                releaseBeforeSend(record);
                return;
            }
            KafkaPublishMessage<Object> message;
            try {
                message = serializer.deserialize(record);
            } catch (RuntimeException e) {
                completeSnapshotFailureBeforePublisher(record, generation, e);
                return;
            }
            KafkaOutboxTraceScope.Scope scope;
            try {
                scope = traceScope.open(record.getTraceId());
            } catch (RuntimeException e) {
                completeTraceOpenFailureBeforePublisher(record, generation, e);
                return;
            }
            PublisherInvocation invocation;
            try {
                try {
                    invocation = publishIfActive(message, generation);
                } finally {
                    closeTraceScope(scope, record);
                }
            } catch (RuntimeException e) {
                completePublisherFailureAfterPublisher(record, generation, e);
                return;
            }
            if (!invocation.isInvoked()) {
                releaseBeforeSend(record);
                return;
            }
            if (invocation.getFuture() == null) {
                completeInvalidResultAfterPublisher(record, generation,
                        new IllegalStateException(SimpleKafkaOutboxConstant.REASON_FUTURE_EMPTY));
                return;
            }
            awaitFuture(record, generation, invocation.getFuture());
        } finally {
            slots.release();
        }
    }

    /**
     * 快照反序列化失败：进入 publisher 前在 gate 内确认代次，失败按 POISON 回写。
     */
    private void completeSnapshotFailureBeforePublisher(OutboxRecordEntity record, long generation,
                                                        RuntimeException cause) {
        if (!isActiveAtPublisherGate(generation)) {
            releaseBeforeSend(record);
            return;
        }
        completeFailure(record, cause, false, snapshotErrorCode(cause),
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SNAPSHOT_FAILURE);
    }

    /**
     * trace 作用域打开失败：进入 publisher 前在 gate 内确认代次，按可重试性回写。
     */
    private void completeTraceOpenFailureBeforePublisher(OutboxRecordEntity record, long generation,
                                                         RuntimeException cause) {
        if (!isActiveAtPublisherGate(generation)) {
            releaseBeforeSend(record);
            return;
        }
        String code = errorCode(cause);
        completeFailure(record, cause, retryPolicy.isRetryable(toRetryContext(record, code), cause), code,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_PUBLISHER_FAILURE);
    }

    /**
     * publisher 同步抛异常：已进入发布入口，在 gate 内确认代次，旧代次直接丢弃不回写。
     */
    private void completePublisherFailureAfterPublisher(OutboxRecordEntity record, long generation,
                                                        RuntimeException cause) {
        if (!isActiveAtPublisherGate(generation)) {
            return;
        }
        String code = errorCode(cause);
        completeFailure(record, cause, retryPolicy.isRetryable(toRetryContext(record, code), cause), code,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_PUBLISHER_FAILURE);
    }

    /**
     * publisher 返回 null Future 或结果 metadata 非法：已进入发布入口，按可重试回写。
     */
    private void completeInvalidResultAfterPublisher(OutboxRecordEntity record, long generation,
                                                     IllegalStateException cause) {
        if (!isActiveAtPublisherGate(generation)) {
            return;
        }
        completeFailure(record, cause, true, ErrorCode.KAFKA_OUTBOX_007,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_INVALID_RESULT);
    }

    /**
     * 关闭 trace 作用域，失败仅告警不影响主流程。
     */
    private void closeTraceScope(KafkaOutboxTraceScope.Scope scope, OutboxRecordEntity record) {
        if (scope == null) {
            return;
        }
        try {
            scope.close();
        } catch (RuntimeException e) {
            log.warn("Kafka Outbox trace 作用域关闭失败，recordId={}, messageId={}",
                    record.getId(), record.getMessageId());
        }
    }

    /**
     * 同步等待 Future 结果：成功回写 broker metadata 并 markSent，各类异常按可重试性回写。
     */
    private void awaitFuture(OutboxRecordEntity record, long generation, ListenableFuture<KafkaPublishResult> future) {
        try {
            KafkaPublishResult result = future.get(properties.getSend().getTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!validResult(record, result)) {
                completeInvalidResultAfterPublisher(record, generation,
                        new IllegalStateException(SimpleKafkaOutboxConstant.REASON_RESULT_METADATA_INVALID));
                return;
            }
            record.setBrokerTopic(result.getTopic());
            record.setBrokerPartition(result.getPartition());
            record.setBrokerOffset(result.getOffset());
            record.setBrokerTimestamp(result.getTimestamp());
            try {
                if (repository.markSent(record)) {
                    notifyListener(EventType.SENT, toEvent(record, OutboxStatus.SENT, null));
                } else {
                    leaseLost(record);
                }
            } catch (RuntimeException e) {
                log.warn("Kafka Outbox SENT 回写失败，recordId={}, messageId={}, errorCode={}",
                        record.getId(), record.getMessageId(), ErrorCode.KAFKA_OUTBOX_006);
            }
        } catch (TimeoutException e) {
            if (isActive(generation)) {
                completeFailure(record, e, true, SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN,
                        SimpleKafkaOutboxConstant.ERROR_SUMMARY_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (isActive(generation)) {
                completeFailure(record, e, true, SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN,
                        SimpleKafkaOutboxConstant.ERROR_SUMMARY_INTERRUPTED);
            }
        } catch (CancellationException e) {
            if (isActive(generation)) {
                completeFailure(record, e, true, SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN,
                        SimpleKafkaOutboxConstant.ERROR_SUMMARY_CANCELLED);
            }
        } catch (ExecutionException e) {
            if (isActive(generation)) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                String code = errorCode(cause);
                completeFailure(record, cause, retryPolicy.isRetryable(toRetryContext(record, code), cause),
                        code, SimpleKafkaOutboxConstant.ERROR_SUMMARY_PUBLISHER_FAILURE);
            }
        }
    }

    /**
     * 失败回写：达 maxAttempts 或不可重试进 POISON，否则进 RETRY_WAIT 并计算退避延迟。CAS 失败转 leaseLost。
     */
    private void completeFailure(OutboxRecordEntity record, Throwable cause, boolean retryable,
                                 String errorCode, String errorSummary) {
        int attempt = record.getAttempt() == null ? SimpleKafkaOutboxConstant.ZERO : record.getAttempt();
        boolean poison = attempt >= properties.getRetry().getMaxAttempts() || !retryable;
        try {
            boolean updated;
            OutboxStatus target;
            if (poison) {
                target = OutboxStatus.POISON;
                updated = repository.markPoison(record, errorCode,
                        KafkaOutboxStringHelper.truncateErrorSummary(errorSummary));
            } else {
                target = OutboxStatus.RETRY_WAIT;
                long delayMs = retryPolicy.nextDelayMs(toRetryContext(record, errorCode));
                updated = repository.markRetry(record,
                        safeMicros(delayMs), errorCode,
                        KafkaOutboxStringHelper.truncateErrorSummary(errorSummary));
            }
            if (updated) {
                notifyListener(poison ? EventType.POISON : EventType.RETRY,
                        toEvent(record, target, errorCode));
            } else {
                leaseLost(record);
            }
        } catch (RuntimeException e) {
            log.warn("Kafka Outbox 失败状态回写失败，recordId={}, messageId={}, errorCode={}",
                    record.getId(), record.getMessageId(), ErrorCode.KAFKA_OUTBOX_006);
        }
    }

    /**
     * 停机发送前条件释放租约：把记录退回 RETRY_WAIT 等待恢复，CAS 失败转 leaseLost。
     */
    private void releaseBeforeSend(OutboxRecordEntity record) {
        try {
            if (!repository.releaseBeforeSend(record, SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                    SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE)) {
                leaseLost(record);
            }
        } catch (RuntimeException e) {
            log.warn("Kafka Outbox 停机释放失败，recordId={}, messageId={}, errorCode={}",
                    record.getId(), record.getMessageId(), ErrorCode.KAFKA_OUTBOX_006);
        }
    }

    /**
     * 在 publisherGate 内原子化检查当前代次并调用 publisher.publish()，返回是否真正进入发布入口。
     */
    private PublisherInvocation publishIfActive(KafkaPublishMessage<Object> message, long generation) {
        synchronized (publisherGate) {
            if (!isActive(generation)) {
                return PublisherInvocation.notInvoked();
            }
            return PublisherInvocation.invoked(publisher.publish(message));
        }
    }

    /**
     * 在 publisherGate 内确认当前代次，保证 stop 已持锁时旧代次任务不再回写状态。
     */
    private boolean isActiveAtPublisherGate(long generation) {
        synchronized (publisherGate) {
            return isActive(generation);
        }
    }

    /**
     * 判断任务代次是否仍活跃：运行中且代次未变。
     */
    private boolean isActive(long generation) {
        return running.get() && lifecycleGeneration.get() == generation;
    }

    /**
     * 校验 publisher 结果：非空、messageId 一致、topic 非空、partition 与 offset 非负。
     */
    private boolean validResult(OutboxRecordEntity record, KafkaPublishResult result) {
        return result != null && record.getMessageId().equals(result.getMessageId())
                && KafkaOutboxStringHelper.hasText(result.getTopic())
                && result.getPartition() != null && result.getPartition() >= SimpleKafkaOutboxConstant.ZERO
                && result.getOffset() != null && result.getOffset() >= SimpleKafkaOutboxConstant.ZERO_LONG;
    }

    /**
     * 毫秒转微秒，溢出时饱和到 Long.MAX_VALUE。
     */
    private long safeMicros(long delayMs) {
        if (delayMs > Long.MAX_VALUE / SimpleKafkaOutboxConstant.MILLIS_TO_MICROS) {
            return Long.MAX_VALUE;
        }
        return delayMs * SimpleKafkaOutboxConstant.MILLIS_TO_MICROS;
    }

    /**
     * 快照反序列化失败的错误码：KafkaOutboxException 取自身码，其余取 KAFKA_OUTBOX_005。
     */
    private String snapshotErrorCode(Throwable cause) {
        return cause instanceof KafkaOutboxException ? ((KafkaOutboxException) cause).getErrorCode()
                : ErrorCode.KAFKA_OUTBOX_005;
    }

    /**
     * 识别失败错误码：KafkaPublishException/KafkaOutboxException 取自身码，其余取 KAFKA_OUTBOX_008。
     */
    private String errorCode(Throwable cause) {
        if (cause instanceof KafkaPublishException) {
            return ((KafkaPublishException) cause).getErrorCode();
        }
        if (cause instanceof KafkaOutboxException) {
            return ((KafkaOutboxException) cause).getErrorCode();
        }
        return ErrorCode.KAFKA_OUTBOX_008;
    }

    /**
     * 构造重试策略上下文，topic/datasourceKey 经脱敏处理。
     */
    private OutboxRetryContext toRetryContext(OutboxRecordEntity record, String errorCode) {
        return OutboxRetryContext.builder()
                .recordId(record.getId()).messageId(record.getMessageId()).status(record.getStatus())
                .attempt(record.getAttempt()).schemaVersion(record.getSchemaVersion())
                .topic(KafkaOutboxStringHelper.safeDisplay(record.getTopic()))
                .datasourceKey(KafkaOutboxStringHelper.safeDisplay(record.getDatasourceKey()))
                .errorCode(errorCode).build();
    }

    /**
     * 构造脱敏事件上下文，供 Listener 回调使用。
     */
    private OutboxEventContext toEvent(OutboxRecordEntity record, OutboxStatus status, String errorCode) {
        return OutboxEventContext.builder()
                .recordId(record.getId()).messageId(record.getMessageId()).status(status.getCode())
                .attempt(record.getAttempt()).schemaVersion(record.getSchemaVersion())
                .topic(KafkaOutboxStringHelper.safeDisplay(record.getTopic()))
                .datasourceKey(KafkaOutboxStringHelper.safeDisplay(record.getDatasourceKey()))
                .errorCode(errorCode).brokerTopic(KafkaOutboxStringHelper.safeDisplay(record.getBrokerTopic()))
                .brokerPartition(record.getBrokerPartition()).brokerOffset(record.getBrokerOffset())
                .brokerTimestamp(record.getBrokerTimestamp()).build();
    }

    /**
     * CAS 回写失败时通知租约丢失，记录保持 PROCESSING 等待恢复。
     */
    private void leaseLost(OutboxRecordEntity record) {
        notifyListener(EventType.LEASE_LOST, toEvent(record, OutboxStatus.PROCESSING, null));
    }

    /**
     * 派发事件到 Listener，Listener 异常仅告警不影响主流程。
     */
    private void notifyListener(EventType type, OutboxEventContext context) {
        try {
            if (type == EventType.CLAIMED) listener.onClaimed(context);
            else if (type == EventType.SENT) listener.onSent(context);
            else if (type == EventType.RETRY) listener.onRetry(context);
            else if (type == EventType.POISON) listener.onPoison(context);
            else listener.onLeaseLost(context);
        } catch (RuntimeException e) {
            log.warn("Kafka Outbox Listener 执行失败，recordId={}, messageId={}",
                    context.getRecordId(), context.getMessageId());
        }
    }

    private enum EventType {CLAIMED, SENT, RETRY, POISON, LEASE_LOST}

    private static final class PublisherInvocation {
        private final boolean invoked;
        private final ListenableFuture<KafkaPublishResult> future;

        private PublisherInvocation(boolean invoked, ListenableFuture<KafkaPublishResult> future) {
            this.invoked = invoked;
            this.future = future;
        }

        private static PublisherInvocation notInvoked() {
            return new PublisherInvocation(false, null);
        }

        private static PublisherInvocation invoked(ListenableFuture<KafkaPublishResult> future) {
            return new PublisherInvocation(true, future);
        }

        private boolean isInvoked() {
            return invoked;
        }

        private ListenableFuture<KafkaPublishResult> getFuture() {
            return future;
        }
    }
}
