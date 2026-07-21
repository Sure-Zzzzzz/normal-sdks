package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.KafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceScope;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.DefaultKafkaOutboxWorker;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 默认 Kafka Outbox Worker 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaOutboxWorkerTest {

    private KafkaOutboxRepository repository;
    private KafkaOutboxMessageSerializer serializer;
    private KafkaOutboxRetryPolicy retryPolicy;
    private KafkaOutboxEventListener listener;
    private KafkaOutboxTraceScope traceScope;
    private KafkaPublisher publisher;
    private SimpleKafkaOutboxProperties properties;
    private ThreadPoolTaskExecutor executor;
    private TaskScheduler scheduler;
    private ScheduledFuture<?> scanFuture;
    private AtomicReference<Runnable> submittedTask;
    private DefaultKafkaOutboxWorker worker;

    @BeforeEach
    public void setUp() {
        repository = mock(KafkaOutboxRepository.class);
        serializer = mock(KafkaOutboxMessageSerializer.class);
        retryPolicy = mock(KafkaOutboxRetryPolicy.class);
        listener = mock(KafkaOutboxEventListener.class);
        traceScope = mock(KafkaOutboxTraceScope.class);
        publisher = mock(KafkaPublisher.class);
        executor = mock(ThreadPoolTaskExecutor.class);
        scheduler = mock(TaskScheduler.class);
        scanFuture = mock(ScheduledFuture.class);
        submittedTask = new AtomicReference<>();

        properties = new SimpleKafkaOutboxProperties();
        properties.getWorker().setConcurrency(1);
        properties.getWorker().setBatchSize(1);
        properties.getWorker().setScanIntervalMs(1L);
        properties.getWorker().setLeaseMs(1000L);
        properties.getWorker().setShutdownAwaitMs(1L);
        properties.getSend().setTimeoutMs(5L);
        properties.getRetry().setMaxAttempts(3);

        KafkaOutboxTraceScope.Scope scope = mock(KafkaOutboxTraceScope.Scope.class);
        when(traceScope.open(any())).thenReturn(scope);
        doReturn(scanFuture).when(scheduler).schedule(any(Runnable.class), any(java.util.Date.class));
        doAnswer(invocation -> {
            submittedTask.set(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));

        worker = new DefaultKafkaOutboxWorker(repository, serializer, retryPolicy, listener,
                traceScope, publisher, properties, executor, scheduler);
        worker.start();
    }

    @AfterEach
    public void tearDown() {
        if (worker.isRunning()) {
            worker.stop();
        }
    }

    @Test
    public void shouldRestoreStoppedStateWhenSchedulerRejectsStart() {
        worker.stop();
        doThrow(new RejectedExecutionException("mock scheduler rejection"))
                .when(scheduler).schedule(any(Runnable.class), any(java.util.Date.class));

        assertThrows(RejectedExecutionException.class, worker::start, "调度器拒绝启动必须向调用方暴露");

        assertFalse(worker.isRunning(), "调度器拒绝后 Worker 不得残留运行状态");
        verify(scheduler, times(2)).schedule(any(Runnable.class), any(java.util.Date.class));
    }

    @Test
    public void shouldMarkSentWhenTraceScopeCloseFailsAfterPublisherReturnsFuture() {
        OutboxRecordEntity record = prepareSuccessfulPublish();
        KafkaOutboxTraceScope.Scope scope = mock(KafkaOutboxTraceScope.Scope.class);
        when(traceScope.open(record.getTraceId())).thenReturn(scope);
        doThrow(new IllegalStateException("mock trace close failure")).when(scope).close();
        when(repository.markSent(record)).thenReturn(true);

        scanAndRunSubmittedTask();

        verify(publisher).publish(any(KafkaPublishMessage.class));
        verify(scope).close();
        verify(repository).markSent(record);
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(listener).onSent(any(OutboxEventContext.class));
    }

    @Test
    public void shouldMarkSentWhenPublishFutureSucceeds() {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaPublishResult result = validResult(record);
        SettableListenableFuture<KafkaPublishResult> future = new SettableListenableFuture<>();
        future.set(result);
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(repository.markSent(record)).thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onSent(eventCaptor.capture());
        log.info("Future 成功输入 recordId={}, messageId={}，输出状态={}, brokerOffset={}",
                record.getId(), record.getMessageId(), eventCaptor.getValue().getStatus(), record.getBrokerOffset());
        assertEquals(OutboxStatus.SENT.getCode(), eventCaptor.getValue().getStatus(),
                "Future 成功后 Listener 状态应为 SENT");
        assertEquals(result.getTopic(), record.getBrokerTopic(), "成功结果 topic 应回填到记录");
        assertEquals(result.getPartition(), record.getBrokerPartition(), "成功结果 partition 应回填到记录");
        assertEquals(result.getOffset(), record.getBrokerOffset(), "成功结果 offset 应回填到记录");
        assertEquals(result.getTimestamp(), record.getBrokerTimestamp(), "成功结果 timestamp 应回填到记录");
    }

    @Test
    public void shouldMarkPoisonWhenPublisherThrowsDeterministicExceptionSynchronously() {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaPublishException exception = new KafkaPublishException(
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_006,
                "mock deterministic failure");
        doThrow(exception).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.isRetryable(any(), eq(exception))).thenReturn(false);
        when(repository.markPoison(eq(record), eq(exception.getErrorCode()), any())).thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onPoison(eventCaptor.capture());
        log.info("同步确定性异常输入 errorCode={}，输出状态={}",
                exception.getErrorCode(), eventCaptor.getValue().getStatus());
        assertEquals(OutboxStatus.POISON.getCode(), eventCaptor.getValue().getStatus(),
                "同步确定性 KafkaPublishException 应进入 POISON");
        assertEquals(exception.getErrorCode(), eventCaptor.getValue().getErrorCode(),
                "POISON 事件应保留 Publisher 错误码");
    }

    @Test
    public void shouldMarkRetryWhenFutureCompletesWithTemporaryFailure() {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaPublishException exception = new KafkaPublishException(
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_007,
                "mock temporary failure");
        SettableListenableFuture<KafkaPublishResult> future = new SettableListenableFuture<>();
        future.setException(exception);
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.isRetryable(any(), eq(exception))).thenReturn(true);
        when(retryPolicy.nextDelayMs(any())).thenReturn(25L);
        when(repository.markRetry(eq(record), eq(25000L), eq(exception.getErrorCode()), any())).thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onRetry(eventCaptor.capture());
        log.info("Future ExecutionException 输入 errorCode={}，输出状态={}，重试延迟微秒={}",
                exception.getErrorCode(), eventCaptor.getValue().getStatus(), 25000L);
        assertEquals(OutboxStatus.RETRY_WAIT.getCode(), eventCaptor.getValue().getStatus(),
                "Future 暂时失败应进入 RETRY_WAIT");
        assertEquals(exception.getErrorCode(), eventCaptor.getValue().getErrorCode(),
                "RETRY 事件应保留 Publisher 错误码");
    }

    @Test
    public void shouldMarkRetryWhenFutureTimesOut() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new TimeoutException("mock timeout"));
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.nextDelayMs(any())).thenReturn(30L);
        when(repository.markRetry(eq(record), eq(30000L),
                eq(SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN), any())).thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onRetry(eventCaptor.capture());
        log.info("Future 超时输入 timeoutMs={}，输出状态={}，errorCode={}",
                properties.getSend().getTimeoutMs(), eventCaptor.getValue().getStatus(),
                eventCaptor.getValue().getErrorCode());
        assertEquals(OutboxStatus.RETRY_WAIT.getCode(), eventCaptor.getValue().getStatus(),
                "TimeoutException 应进入 RETRY_WAIT");
        assertEquals(SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN,
                eventCaptor.getValue().getErrorCode(), "超时重试应标记发送结果未知错误码");
    }

    @Test
    public void shouldLeaveProcessingRecordForLeaseRecoveryWhenMarkSentThrows() {
        OutboxRecordEntity record = prepareSuccessfulPublish();
        doThrow(new IllegalStateException("mock persistence failure")).when(repository).markSent(record);

        scanAndRunSubmittedTask();

        verify(repository).markSent(record);
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
        verify(listener, never()).onSent(any());
        verify(listener, never()).onRetry(any());
        verify(listener, never()).onPoison(any());
        verify(listener, never()).onLeaseLost(any());
    }

    @Test
    public void shouldNotifyLeaseLostWhenMarkSentReturnsFalse() {
        OutboxRecordEntity record = prepareSuccessfulPublish();
        when(repository.markSent(record)).thenReturn(false);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onLeaseLost(eventCaptor.capture());
        log.info("markSent 输入 recordId={}，返回 false，输出事件状态={}",
                record.getId(), eventCaptor.getValue().getStatus());
        assertEquals(OutboxStatus.PROCESSING.getCode(), eventCaptor.getValue().getStatus(),
                "markSent 返回 false 应通知租约丢失且保持 PROCESSING 语义");
        verify(listener, never()).onSent(any());
    }

    @Test
    public void shouldNotifyLeaseLostWhenMarkRetryReturnsFalse() {
        OutboxRecordEntity record = prepareTemporaryFailure();
        when(repository.markRetry(eq(record), anyLong(), any(), any())).thenReturn(false);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onLeaseLost(eventCaptor.capture());
        log.info("markRetry 输入 recordId={}，返回 false，输出事件状态={}",
                record.getId(), eventCaptor.getValue().getStatus());
        assertEquals(OutboxStatus.PROCESSING.getCode(), eventCaptor.getValue().getStatus(),
                "markRetry 返回 false 应通知租约丢失且保持 PROCESSING 语义");
        verify(listener, never()).onRetry(any());
    }

    @Test
    public void shouldNotifyLeaseLostWhenMarkPoisonReturnsFalse() {
        OutboxRecordEntity record = prepareDeterministicFailure();
        when(repository.markPoison(eq(record), any(), any())).thenReturn(false);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onLeaseLost(eventCaptor.capture());
        log.info("markPoison 输入 recordId={}，返回 false，输出事件状态={}",
                record.getId(), eventCaptor.getValue().getStatus());
        assertEquals(OutboxStatus.PROCESSING.getCode(), eventCaptor.getValue().getStatus(),
                "markPoison 返回 false 应通知租约丢失且保持 PROCESSING 语义");
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldReleaseClaimedRecordBeforeSendWhenStopped() {
        OutboxRecordEntity record = prepareClaimedRecord();
        when(repository.releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE)).thenReturn(true);

        worker.scanOnce();
        Runnable task = submittedTask.get();
        log.info("停机前扫描输出受控任务: {}", task);
        assertNotNull(task, "扫描领取后应向受控 executor 提交待执行任务");
        boolean runningBeforeStop = worker.isRunning();
        worker.stop();
        boolean runningAfterStop = worker.isRunning();
        task.run();

        verify(repository).releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE);
        log.info("停机释放输入 runningBeforeStop={}、runningAfterStop={}，输出 recordId={} 已释放",
                runningBeforeStop, runningAfterStop, record.getId());
        assertTrue(runningBeforeStop, "start 后 Worker 应处于运行状态");
        assertFalse(runningAfterStop, "stop 后 Worker 应处于停止状态");
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
        verify(listener, never()).onLeaseLost(any());
        verify(scanFuture).cancel(false);
    }

    @Test
    public void shouldKeepSentStateWhenListenerThrowsException() {
        OutboxRecordEntity record = prepareSuccessfulPublish();
        when(repository.markSent(record)).thenReturn(true);
        doThrow(new IllegalStateException("mock listener failure")).when(listener).onSent(any());

        scanAndRunSubmittedTask();

        log.info("Listener 异常输入 recordId={}，输出 markSent 已执行且未转入失败状态", record.getId());
        assertEquals("mock-topic", record.getBrokerTopic(), "Listener 异常不应清除已回填的 broker topic");
        assertEquals(Long.valueOf(9L), record.getBrokerOffset(), "Listener 异常不应清除已回填的 broker offset");
        verify(repository).markSent(record);
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(listener, never()).onLeaseLost(any());
    }

    @Test
    public void shouldMarkPoisonWhenRetryableFailureReachesMaxAttempts() {
        OutboxRecordEntity record = prepareTemporaryFailure();
        record.setAttempt(properties.getRetry().getMaxAttempts());
        when(repository.markPoison(eq(record), any(), any())).thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(repository).markPoison(eq(record),
                eq(io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_007), any());
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(retryPolicy, never()).nextDelayMs(any());
        verify(listener).onPoison(eventCaptor.capture());
        assertEquals(OutboxStatus.POISON.getCode(), eventCaptor.getValue().getStatus(),
                "达到最大尝试次数的临时失败必须终止为 POISON");
        assertEquals(io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_007,
                eventCaptor.getValue().getErrorCode(), "终态必须保留原始 Publisher 错误码");
    }

    @Test
    public void shouldReleaseClaimedRecordWhenExecutorRejectsTask() {
        OutboxRecordEntity record = prepareClaimedRecord();
        when(repository.claim(1, 1000000L)).thenReturn(Collections.singletonList(record), Collections.emptyList());
        doThrow(new RejectedExecutionException("mock executor rejection")).when(executor).execute(any(Runnable.class));
        when(repository.releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE)).thenReturn(true);

        worker.scanOnce();
        worker.scanOnce();

        verify(repository, times(2)).claim(1, 1000000L);
        verify(repository).releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE);
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
    }

    @Test
    public void shouldMarkRetryWhenPublisherReturnsNullFuture() {
        OutboxRecordEntity record = prepareClaimedRecord();
        doReturn(null).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.nextDelayMs(any())).thenReturn(10L);
        when(repository.markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_007), any()))
                .thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onRetry(eventCaptor.capture());
        log.info("Publisher 返回 null Future，输出状态={}，errorCode={}",
                eventCaptor.getValue().getStatus(), eventCaptor.getValue().getErrorCode());
        assertEquals(OutboxStatus.RETRY_WAIT.getCode(), eventCaptor.getValue().getStatus(),
                "null Future 应进入 RETRY_WAIT");
        assertEquals(ErrorCode.KAFKA_OUTBOX_007, eventCaptor.getValue().getErrorCode(),
                "null Future 应使用非法结果错误码");
    }

    @Test
    public void shouldMarkRetryWhenPublishResultMetadataIsInvalid() {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaPublishResult invalidResult = KafkaPublishResult.builder()
                .messageId(record.getMessageId())
                .topic("mock-topic")
                .partition(-1)
                .offset(9L)
                .timestamp(10L)
                .build();
        SettableListenableFuture<KafkaPublishResult> future = new SettableListenableFuture<>();
        future.set(invalidResult);
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.nextDelayMs(any())).thenReturn(10L);
        when(repository.markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_007), any()))
                .thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(listener).onRetry(eventCaptor.capture());
        log.info("非法结果输入 partition={}，输出状态={}，errorCode={}",
                invalidResult.getPartition(), eventCaptor.getValue().getStatus(),
                eventCaptor.getValue().getErrorCode());
        assertEquals(OutboxStatus.RETRY_WAIT.getCode(), eventCaptor.getValue().getStatus(),
                "非法 broker metadata 应进入 RETRY_WAIT");
        assertEquals(ErrorCode.KAFKA_OUTBOX_007, eventCaptor.getValue().getErrorCode(),
                "非法 broker metadata 应使用非法结果错误码");
    }

    @Test
    public void shouldMarkRetryForEveryInvalidPublishResultMetadataField() {
        OutboxRecordEntity record = prepareClaimedRecord();
        List<KafkaPublishResult> invalidResults = Arrays.asList(
                null,
                KafkaPublishResult.builder().messageId(record.getMessageId()).topic(" ")
                        .partition(2).offset(9L).timestamp(10L).build(),
                KafkaPublishResult.builder().messageId(record.getMessageId()).topic("mock-topic")
                        .offset(9L).timestamp(10L).build(),
                KafkaPublishResult.builder().messageId(record.getMessageId()).topic("mock-topic")
                        .partition(-1).offset(9L).timestamp(10L).build(),
                KafkaPublishResult.builder().messageId(record.getMessageId()).topic("mock-topic")
                        .partition(2).timestamp(10L).build(),
                KafkaPublishResult.builder().messageId(record.getMessageId()).topic("mock-topic")
                        .partition(2).offset(-1L).timestamp(10L).build());
        java.util.concurrent.atomic.AtomicInteger resultIndex = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            SettableListenableFuture<KafkaPublishResult> future = new SettableListenableFuture<>();
            future.set(invalidResults.get(resultIndex.getAndIncrement()));
            return future;
        }).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.nextDelayMs(any())).thenReturn(10L);
        when(repository.markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_007), any())).thenReturn(true);

        for (int index = 0; index < invalidResults.size(); index++) {
            scanAndRunSubmittedTask();
        }

        verify(repository, times(invalidResults.size())).markRetry(eq(record), eq(10000L),
                eq(ErrorCode.KAFKA_OUTBOX_007), any());
        verify(repository, never()).markSent(any());
        verify(listener, times(invalidResults.size())).onRetry(any(OutboxEventContext.class));
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldNotClaimMoreRecordsUntilConcurrentTasksReleaseSlots() {
        properties.getWorker().setConcurrency(2);
        properties.getWorker().setBatchSize(3);
        properties.getSend().setTimeoutMs(10000L);
        worker.stop();
        worker = new DefaultKafkaOutboxWorker(repository, serializer, retryPolicy, listener,
                traceScope, publisher, properties, executor, scheduler);
        worker.start();
        OutboxRecordEntity first = record(1L, "first-message");
        OutboxRecordEntity second = record(2L, "second-message");
        OutboxRecordEntity third = record(3L, "third-message");
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(repository.claim(2, 1000000L)).thenReturn(Arrays.asList(first, second));
        when(repository.claim(1, 1000000L)).thenReturn(Collections.singletonList(third));
        SettableListenableFuture<KafkaPublishResult> firstFuture = new SettableListenableFuture<>();
        SettableListenableFuture<KafkaPublishResult> secondFuture = new SettableListenableFuture<>();
        SettableListenableFuture<KafkaPublishResult> thirdFuture = new SettableListenableFuture<>();
        when(serializer.deserialize(first)).thenReturn(message(first));
        when(serializer.deserialize(second)).thenReturn(message(second));
        when(serializer.deserialize(third)).thenReturn(message(third));
        doAnswer(invocation -> {
            KafkaPublishMessage<?> publishMessage = invocation.getArgument(0);
            if (first.getMessageId().equals(publishMessage.getMessageId())) {
                return firstFuture;
            }
            if (second.getMessageId().equals(publishMessage.getMessageId())) {
                return secondFuture;
            }
            return thirdFuture;
        }).when(publisher).publish(any(KafkaPublishMessage.class));
        when(repository.markSent(any())).thenReturn(true);

        worker.scanOnce();
        assertEquals(2, tasks.size(), "并发上限为 2 时首次扫描只能提交两条任务");
        Thread firstThread = new Thread(tasks.get(0));
        Thread secondThread = new Thread(tasks.get(1));
        firstThread.start();
        secondThread.start();
        awaitPublisherCalls(2);
        worker.scanOnce();
        verify(repository, never()).claim(1, 1000000L);

        firstFuture.set(validResult(first));
        join(firstThread);
        worker.scanOnce();
        assertEquals(3, tasks.size(), "释放一个 slot 后下一次扫描才可以提交第三条任务");

        secondFuture.set(validResult(second));
        thirdFuture.set(validResult(third));
        join(secondThread);
        tasks.get(2).run();
        verify(repository).claim(1, 1000000L);
        verify(repository, times(3)).markSent(any());
    }

    @Test
    public void shouldMarkPoisonWithoutPublishingWhenSnapshotDeserializationFails() {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaOutboxException exception = new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_005, "mock snapshot failure");
        doThrow(exception).when(serializer).deserialize(record);
        when(repository.markPoison(eq(record), eq(ErrorCode.KAFKA_OUTBOX_005), any())).thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(repository).markPoison(eq(record), eq(ErrorCode.KAFKA_OUTBOX_005), any());
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
        verify(retryPolicy, never()).isRetryable(any(), any());
        verify(retryPolicy, never()).nextDelayMs(any());
        verify(listener).onPoison(eventCaptor.capture());
        assertEquals(OutboxStatus.POISON.getCode(), eventCaptor.getValue().getStatus(),
                "快照重建失败必须直接进入 POISON");
        assertEquals(ErrorCode.KAFKA_OUTBOX_005, eventCaptor.getValue().getErrorCode(),
                "快照重建失败必须保留序列化错误码");
    }

    @Test
    public void shouldMarkRetryWhenPublishResultMessageIdDoesNotMatchSnapshot() {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaPublishResult invalidResult = KafkaPublishResult.builder()
                .messageId("other-message-id")
                .topic("mock-topic")
                .partition(2)
                .offset(9L)
                .timestamp(10L)
                .build();
        SettableListenableFuture<KafkaPublishResult> future = new SettableListenableFuture<>();
        future.set(invalidResult);
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.nextDelayMs(any())).thenReturn(10L);
        when(repository.markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_007), any()))
                .thenReturn(true);

        scanAndRunSubmittedTask();

        ArgumentCaptor<OutboxEventContext> eventCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);
        verify(repository, never()).markSent(any());
        verify(repository).markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_007), any());
        verify(listener).onRetry(eventCaptor.capture());
        assertEquals(OutboxStatus.RETRY_WAIT.getCode(), eventCaptor.getValue().getStatus(),
                "messageId 不一致的回执必须进入 RETRY_WAIT");
        assertEquals(ErrorCode.KAFKA_OUTBOX_007, eventCaptor.getValue().getErrorCode(),
                "messageId 不一致的回执必须标记为非法结果");
    }

    @Test
    public void shouldCloseTraceScopeAfterSuccessfulPublish() {
        OutboxRecordEntity record = prepareSuccessfulPublish();
        KafkaOutboxTraceScope.Scope scope = mock(KafkaOutboxTraceScope.Scope.class);
        when(traceScope.open(record.getTraceId())).thenReturn(scope);
        when(repository.markSent(record)).thenReturn(true);

        scanAndRunSubmittedTask();

        verify(traceScope).open(record.getTraceId());
        verify(scope).close();
        verify(repository).markSent(record);
    }

    @Test
    public void shouldNotClaimWhenWorkerIsStopped() {
        worker.stop();

        worker.scanOnce();

        verify(repository, never()).claim(org.mockito.ArgumentMatchers.anyInt(), anyLong());
    }

    @Test
    public void shouldIgnoreClaimFailureWithoutSubmittingTask() {
        doThrow(new IllegalStateException("mock claim failure")).when(repository).claim(org.mockito.ArgumentMatchers.anyInt(), anyLong());

        worker.scanOnce();

        assertFalse(submittedTask.get() != null, "领取失败不得提交未知任务");
        verify(repository).claim(1, 1000000L);
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
    }

    @Test
    public void shouldNotifyLeaseLostWhenShutdownReleaseReturnsFalse() {
        OutboxRecordEntity record = prepareClaimedRecord();
        when(repository.releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE)).thenReturn(false);

        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        worker.stop();
        task.run();

        verify(listener).onLeaseLost(any(OutboxEventContext.class));
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
    }

    @Test
    public void shouldLeaveProcessingRecordWhenMarkRetryThrows() {
        OutboxRecordEntity record = prepareTemporaryFailure();
        doThrow(new IllegalStateException("mock retry persistence failure"))
                .when(repository).markRetry(eq(record), anyLong(), any(), any());

        scanAndRunSubmittedTask();

        verify(repository).markRetry(eq(record), anyLong(), any(), any());
        verify(listener, never()).onRetry(any());
        verify(listener, never()).onLeaseLost(any());
        verify(repository, never()).markPoison(any(), any(), any());
    }

    @Test
    public void shouldSaturateRetryDelayMicrosWhenDelayOverflows() {
        OutboxRecordEntity record = prepareTemporaryFailure();
        when(retryPolicy.nextDelayMs(any())).thenReturn(Long.MAX_VALUE);
        when(repository.markRetry(eq(record), eq(Long.MAX_VALUE), any(), any())).thenReturn(true);

        scanAndRunSubmittedTask();

        verify(repository).markRetry(eq(record), eq(Long.MAX_VALUE), any(), any());
        verify(listener).onRetry(any(OutboxEventContext.class));
    }

    @Test
    public void shouldMarkRetryWhenPublishFutureIsCancelledWhileRunning() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new java.util.concurrent.CancellationException("mock cancelled"));
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.nextDelayMs(any())).thenReturn(10L);
        when(repository.markRetry(eq(record), eq(10000L),
                eq(SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN), any())).thenReturn(true);

        scanAndRunSubmittedTask();

        verify(repository).markRetry(eq(record), eq(10000L),
                eq(SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN), any());
        verify(listener).onRetry(any(OutboxEventContext.class));
        verify(repository, never()).markPoison(any(), any(), any());
    }

    @Test
    public void shouldMarkRetryWhenPublisherThrowsUnknownExceptionSynchronously() {
        OutboxRecordEntity record = prepareClaimedRecord();
        IllegalStateException exception = new IllegalStateException("mock publisher failure");
        doThrow(exception).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.isRetryable(any(), eq(exception))).thenReturn(true);
        when(retryPolicy.nextDelayMs(any())).thenReturn(10L);
        when(repository.markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_008), any()))
                .thenReturn(true);

        scanAndRunSubmittedTask();

        verify(repository).markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_008), any());
        verify(listener).onRetry(any(OutboxEventContext.class));
        verify(repository, never()).markPoison(any(), any(), any());
    }

    @Test
    public void shouldLeaveProcessingRecordWhenMarkPoisonThrows() {
        OutboxRecordEntity record = prepareDeterministicFailure();
        doThrow(new IllegalStateException("mock poison persistence failure"))
                .when(repository).markPoison(eq(record), any(), any());

        scanAndRunSubmittedTask();

        verify(repository).markPoison(eq(record), any(), any());
        verify(listener, never()).onPoison(any());
        verify(listener, never()).onLeaseLost(any());
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
    }

    @Test
    public void shouldIgnoreNullAndEmptyClaimResultsWithoutSubmittingTasks() {
        when(repository.claim(1, 1000000L)).thenReturn(null, Collections.emptyList());

        worker.scanOnce();
        worker.scanOnce();

        verify(repository, times(2)).claim(1, 1000000L);
        assertFalse(submittedTask.get() != null, "空领取结果不得提交任务");
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
    }

    @Test
    public void shouldPreserveInterruptAndMarkRetryWhenFutureIsInterruptedWhileRunning() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("mock interrupted"));
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.nextDelayMs(any())).thenReturn(10L);
        when(repository.markRetry(eq(record), eq(10000L),
                eq(SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN), any())).thenReturn(true);
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        Thread processThread = new Thread(() -> {
            task.run();
            interrupted.set(Thread.currentThread().isInterrupted());
        }, "mock-outbox-interrupted-future");

        processThread.start();
        join(processThread);

        assertTrue(interrupted.get(), "Worker 处理线程必须保留 Future 中断标记");
        verify(repository).markRetry(eq(record), eq(10000L),
                eq(SimpleKafkaOutboxConstant.ERROR_CODE_SEND_UNKNOWN), any());
        verify(listener).onRetry(any(OutboxEventContext.class));
        verify(repository, never()).markPoison(any(), any(), any());
    }

    @Test
    public void shouldNotWriteFailureStateWhenFutureIsCancelledAfterWorkerStops() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        java.util.concurrent.CountDownLatch awaitingFuture = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseCancellation = new java.util.concurrent.CountDownLatch(1);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            awaitingFuture.countDown();
            releaseCancellation.await(1L, TimeUnit.SECONDS);
            throw new java.util.concurrent.CancellationException("mock cancelled after stop");
        });
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-cancelled-after-stop");

        processThread.start();
        assertTrue(awaitingFuture.await(1L, TimeUnit.SECONDS), "Worker 必须进入 Future 等待阶段");
        worker.stop();
        releaseCancellation.countDown();
        join(processThread);

        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
        verify(listener, never()).onRetry(any());
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldReleaseBeforePublisherWhenSnapshotFailsAfterWorkerStops() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        java.util.concurrent.CountDownLatch deserializing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseFailure = new java.util.concurrent.CountDownLatch(1);
        KafkaOutboxException exception = new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_005,
                "mock snapshot failure after stop");
        when(serializer.deserialize(record)).thenAnswer(invocation -> {
            deserializing.countDown();
            releaseFailure.await(1L, TimeUnit.SECONDS);
            throw exception;
        });
        when(repository.releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE)).thenReturn(true);
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-snapshot-failure-after-stop");

        processThread.start();
        assertTrue(deserializing.await(1L, TimeUnit.SECONDS), "Worker 必须进入快照重建阶段");
        worker.stop();
        releaseFailure.countDown();
        join(processThread);

        verify(repository).releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE);
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldReleaseBeforePublisherWhenTraceScopeOpenFailsAfterWorkerStops() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        java.util.concurrent.CountDownLatch openingScope = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseFailure = new java.util.concurrent.CountDownLatch(1);
        IllegalStateException exception = new IllegalStateException("mock trace open failure after stop");
        when(traceScope.open(record.getTraceId())).thenAnswer(invocation -> {
            openingScope.countDown();
            releaseFailure.await(1L, TimeUnit.SECONDS);
            throw exception;
        });
        when(repository.releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE)).thenReturn(true);
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-trace-open-failure-after-stop");

        processThread.start();
        assertTrue(openingScope.await(1L, TimeUnit.SECONDS), "Worker 必须进入 trace 作用域打开阶段");
        worker.stop();
        releaseFailure.countDown();
        join(processThread);

        verify(repository).releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE);
        verify(retryPolicy, never()).isRetryable(any(), any());
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
    }

    @Test
    public void shouldReleaseBeforePublisherWhenWorkerStopsAfterTraceScopeOpens() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaOutboxTraceScope.Scope scope = mock(KafkaOutboxTraceScope.Scope.class);
        java.util.concurrent.CountDownLatch scopeOpened = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseScope = new java.util.concurrent.CountDownLatch(1);
        when(traceScope.open(record.getTraceId())).thenAnswer(invocation -> {
            scopeOpened.countDown();
            releaseScope.await(1L, TimeUnit.SECONDS);
            return scope;
        });
        when(repository.releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE)).thenReturn(true);
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-stop-before-publisher");

        processThread.start();
        assertTrue(scopeOpened.await(1L, TimeUnit.SECONDS), "Worker 必须在发布前打开 trace 作用域");
        worker.stop();
        releaseScope.countDown();
        join(processThread);

        verify(scope).close();
        verify(repository).releaseBeforeSend(record,
                SimpleKafkaOutboxConstant.ERROR_CODE_SHUTDOWN_RELEASE,
                SimpleKafkaOutboxConstant.ERROR_SUMMARY_SHUTDOWN_RELEASE);
        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
        verify(repository, never()).markSent(any());
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
    }

    @Test
    public void shouldNotWriteFailureStateWhenFutureTimesOutAfterWorkerStops() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        java.util.concurrent.CountDownLatch awaitingFuture = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseTimeout = new java.util.concurrent.CountDownLatch(1);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            awaitingFuture.countDown();
            releaseTimeout.await(1L, TimeUnit.SECONDS);
            throw new TimeoutException("mock timeout after stop");
        });
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-timeout-after-stop");

        processThread.start();
        assertTrue(awaitingFuture.await(1L, TimeUnit.SECONDS), "Worker 必须进入 Future 等待阶段");
        worker.stop();
        releaseTimeout.countDown();
        join(processThread);

        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
        verify(listener, never()).onRetry(any());
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldNotWriteFailureStateWhenFutureFailsAfterWorkerStops() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        KafkaPublishException exception = new KafkaPublishException(
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_007,
                "mock failure after stop");
        java.util.concurrent.CountDownLatch awaitingFuture = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseFailure = new java.util.concurrent.CountDownLatch(1);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            awaitingFuture.countDown();
            releaseFailure.await(1L, TimeUnit.SECONDS);
            throw new java.util.concurrent.ExecutionException(exception);
        });
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-failure-after-stop");

        processThread.start();
        assertTrue(awaitingFuture.await(1L, TimeUnit.SECONDS), "Worker 必须进入 Future 等待阶段");
        worker.stop();
        releaseFailure.countDown();
        join(processThread);

        verify(retryPolicy, never()).isRetryable(any(), any());
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
        verify(listener, never()).onRetry(any());
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldMarkSentWhenOldGenerationFutureSucceedsAfterWorkerRestarts() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        java.util.concurrent.CountDownLatch awaitingFuture = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseResult = new java.util.concurrent.CountDownLatch(1);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            awaitingFuture.countDown();
            releaseResult.await(1L, TimeUnit.SECONDS);
            return validResult(record);
        });
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(repository.markSent(record)).thenReturn(true);
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-old-generation-success");

        processThread.start();
        assertTrue(awaitingFuture.await(1L, TimeUnit.SECONDS), "旧代 Worker 必须进入 Future 等待阶段");
        worker.stop();
        worker.start();
        releaseResult.countDown();
        join(processThread);

        verify(repository).markSent(record);
        verify(listener).onSent(any(OutboxEventContext.class));
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
    }

    @Test
    public void shouldOnlyNotifyLeaseLostWhenOldGenerationMarkSentReturnsFalseAfterWorkerRestarts() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        java.util.concurrent.CountDownLatch awaitingFuture = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseResult = new java.util.concurrent.CountDownLatch(1);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            awaitingFuture.countDown();
            releaseResult.await(1L, TimeUnit.SECONDS);
            return validResult(record);
        });
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(repository.markSent(record)).thenReturn(false);
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-old-generation-lease-lost");

        processThread.start();
        assertTrue(awaitingFuture.await(1L, TimeUnit.SECONDS), "旧代 Worker 必须进入 Future 等待阶段");
        worker.stop();
        worker.start();
        releaseResult.countDown();
        join(processThread);

        verify(repository).markSent(record);
        verify(listener).onLeaseLost(any(OutboxEventContext.class));
        verify(listener, never()).onSent(any());
        verify(retryPolicy, never()).isRetryable(any(), any());
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
    }

    @Test
    public void shouldNotWriteFailureStateWhenOldGenerationFutureFailsAfterWorkerRestarts() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        KafkaPublishException exception = new KafkaPublishException(
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_007,
                "mock old generation failure");
        java.util.concurrent.CountDownLatch awaitingFuture = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseFailure = new java.util.concurrent.CountDownLatch(1);
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            awaitingFuture.countDown();
            releaseFailure.await(1L, TimeUnit.SECONDS);
            throw new java.util.concurrent.ExecutionException(exception);
        });
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-old-generation-failure");

        processThread.start();
        assertTrue(awaitingFuture.await(1L, TimeUnit.SECONDS), "旧代 Worker 必须进入 Future 等待阶段");
        worker.stop();
        worker.start();
        releaseFailure.countDown();
        join(processThread);

        verify(retryPolicy, never()).isRetryable(any(), any());
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
        verify(listener, never()).onRetry(any());
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldWaitForSynchronousPublisherFailureBeforeStopping() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        java.util.concurrent.CountDownLatch publisherEntered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseFailure = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch stopReturned = new java.util.concurrent.CountDownLatch(1);
        IllegalStateException exception = new IllegalStateException("mock publisher failure after stop");
        doAnswer(invocation -> {
            publisherEntered.countDown();
            releaseFailure.await(1L, TimeUnit.SECONDS);
            throw exception;
        }).when(publisher).publish(any(KafkaPublishMessage.class));
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-publisher-failure-after-stop");
        Thread stopThread = new Thread(() -> {
            worker.stop();
            stopReturned.countDown();
        }, "mock-outbox-stop-during-publisher-failure");

        processThread.start();
        assertTrue(publisherEntered.await(1L, TimeUnit.SECONDS), "Worker 必须进入 publisher 调用");
        stopThread.start();
        assertFalse(stopReturned.await(100L, TimeUnit.MILLISECONDS), "同步 publisher 尚未返回时 stop 不得越过发布入口");
        releaseFailure.countDown();
        join(processThread);
        join(stopThread);

        verify(retryPolicy, never()).isRetryable(any(), any());
        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
    }

    @Test
    public void shouldNotWriteFailureStateWhenNullFutureReturnsAfterWorkerStops() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaOutboxTraceScope.Scope scope = mock(KafkaOutboxTraceScope.Scope.class);
        java.util.concurrent.CountDownLatch publisherReturned = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseClose = new java.util.concurrent.CountDownLatch(1);
        when(traceScope.open(record.getTraceId())).thenReturn(scope);
        doReturn(null).when(publisher).publish(any(KafkaPublishMessage.class));
        doAnswer(invocation -> {
            publisherReturned.countDown();
            releaseClose.await(1L, TimeUnit.SECONDS);
            return null;
        }).when(scope).close();
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-null-future-after-stop");

        processThread.start();
        assertTrue(publisherReturned.await(1L, TimeUnit.SECONDS), "publisher 必须返回 null Future 后进入 scope 清理");
        worker.stop();
        releaseClose.countDown();
        join(processThread);

        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
        verify(listener, never()).onRetry(any());
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldNotWriteFailureStateWhenInvalidResultArrivesAfterWorkerStops() throws Exception {
        OutboxRecordEntity record = prepareClaimedRecord();
        ListenableFuture<KafkaPublishResult> future = mock(ListenableFuture.class);
        java.util.concurrent.CountDownLatch awaitingFuture = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseResult = new java.util.concurrent.CountDownLatch(1);
        KafkaPublishResult invalidResult = KafkaPublishResult.builder()
                .messageId(record.getMessageId()).topic("mock-topic").partition(-1).offset(9L).build();
        when(future.get(eq(properties.getSend().getTimeoutMs()), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            awaitingFuture.countDown();
            releaseResult.await(1L, TimeUnit.SECONDS);
            return invalidResult;
        });
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        worker.scanOnce();
        Runnable task = submittedTask.get();
        assertNotNull(task, "扫描领取后必须保留待执行任务");
        Thread processThread = new Thread(task, "mock-outbox-invalid-result-after-stop");

        processThread.start();
        assertTrue(awaitingFuture.await(1L, TimeUnit.SECONDS), "Worker 必须进入 Future 等待阶段");
        worker.stop();
        releaseResult.countDown();
        join(processThread);

        verify(repository, never()).markRetry(any(), anyLong(), any(), any());
        verify(repository, never()).markPoison(any(), any(), any());
        verify(repository, never()).releaseBeforeSend(any(), any(), any());
        verify(listener, never()).onRetry(any());
        verify(listener, never()).onPoison(any());
    }

    @Test
    public void shouldMarkRetryWithoutPublishingWhenTraceScopeOpenFails() {
        OutboxRecordEntity record = prepareClaimedRecord();
        IllegalStateException exception = new IllegalStateException("mock trace open failure");
        doThrow(exception).when(traceScope).open(record.getTraceId());
        when(retryPolicy.isRetryable(any(), eq(exception))).thenReturn(true);
        when(retryPolicy.nextDelayMs(any())).thenReturn(10L);
        when(repository.markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_008), any())).thenReturn(true);

        scanAndRunSubmittedTask();

        verify(publisher, never()).publish(any(KafkaPublishMessage.class));
        verify(repository).markRetry(eq(record), eq(10000L), eq(ErrorCode.KAFKA_OUTBOX_008), any());
        verify(listener).onRetry(any(OutboxEventContext.class));
        verify(repository, never()).markPoison(any(), any(), any());
    }

    private OutboxRecordEntity record(long id, String messageId) {
        return OutboxRecordEntity.builder()
                .id(id)
                .messageId(messageId)
                .topic("mock-topic")
                .datasourceKey("mock-datasource")
                .traceId("mock-trace-id")
                .schemaVersion(1)
                .status(OutboxStatus.PROCESSING.getCode())
                .attempt(1)
                .build();
    }

    private KafkaPublishMessage<Object> message(OutboxRecordEntity record) {
        return KafkaPublishMessage.builder()
                .messageId(record.getMessageId())
                .topic(record.getTopic())
                .payload("mock-payload")
                .build();
    }

    private void awaitPublisherCalls(int expectedCalls) {
        long deadline = System.currentTimeMillis() + 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(publisher, times(expectedCalls)).publish(any(KafkaPublishMessage.class));
                return;
            } catch (AssertionError ignored) {
                Thread.yield();
            }
        }
        verify(publisher, times(expectedCalls)).publish(any(KafkaPublishMessage.class));
    }

    private void join(Thread thread) {
        try {
            thread.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Worker 任务结束时线程被中断", e);
        }
        assertFalse(thread.isAlive(), "Worker 任务必须在 Future 完成后释放 slot");
    }

    private OutboxRecordEntity prepareClaimedRecord() {
        OutboxRecordEntity record = OutboxRecordEntity.builder()
                .id(1L)
                .messageId("mock-message-id")
                .topic("mock-topic")
                .datasourceKey("mock-datasource")
                .traceId("mock-trace-id")
                .schemaVersion(1)
                .status(OutboxStatus.PROCESSING.getCode())
                .attempt(1)
                .build();
        KafkaPublishMessage<Object> message = KafkaPublishMessage.builder()
                .messageId(record.getMessageId())
                .topic(record.getTopic())
                .payload("mock-payload")
                .build();
        when(repository.claim(1, 1000000L)).thenReturn(Collections.singletonList(record));
        when(serializer.deserialize(record)).thenReturn(message);
        return record;
    }

    private OutboxRecordEntity prepareSuccessfulPublish() {
        OutboxRecordEntity record = prepareClaimedRecord();
        SettableListenableFuture<KafkaPublishResult> future = new SettableListenableFuture<>();
        future.set(validResult(record));
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        return record;
    }

    private OutboxRecordEntity prepareTemporaryFailure() {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaPublishException exception = new KafkaPublishException(
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_007,
                "mock temporary failure");
        SettableListenableFuture<KafkaPublishResult> future = new SettableListenableFuture<>();
        future.setException(exception);
        doReturn(future).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.isRetryable(any(), eq(exception))).thenReturn(true);
        when(retryPolicy.nextDelayMs(any())).thenReturn(25L);
        return record;
    }

    private OutboxRecordEntity prepareDeterministicFailure() {
        OutboxRecordEntity record = prepareClaimedRecord();
        KafkaPublishException exception = new KafkaPublishException(
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_006,
                "mock deterministic failure");
        doThrow(exception).when(publisher).publish(any(KafkaPublishMessage.class));
        when(retryPolicy.isRetryable(any(), eq(exception))).thenReturn(false);
        return record;
    }

    private KafkaPublishResult validResult(OutboxRecordEntity record) {
        return KafkaPublishResult.builder()
                .messageId(record.getMessageId())
                .topic("mock-topic")
                .partition(2)
                .offset(9L)
                .timestamp(10L)
                .build();
    }

    private void scanAndRunSubmittedTask() {
        worker.scanOnce();
        Runnable task = submittedTask.get();
        log.info("扫描输出受控任务: {}", task);
        assertNotNull(task, "扫描领取记录后应向 executor 提交任务");
        task.run();
    }
}
