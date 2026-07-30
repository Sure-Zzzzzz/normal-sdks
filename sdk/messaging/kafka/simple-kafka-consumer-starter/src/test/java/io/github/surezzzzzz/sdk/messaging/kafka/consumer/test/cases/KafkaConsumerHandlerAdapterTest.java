package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ConsumerEventType;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.ErrorHandlerDecision;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.ErrorHandlerOutcome;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.KafkaConsumerErrorHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandlerAdapter;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireResult;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyLease;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.KafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消费处理适配器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerHandlerAdapterTest {

    private static final String TOPIC = "mock-topic";
    private static final String DATASOURCE = "mock-datasource";
    private static final String GROUP = "mock-group";

    @Test
    public void testSuccessfulHandlerCompletesLeaseBeforeAcknowledgment() throws Exception {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.complete()).thenReturn(true);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        KafkaConsumerHandlerAdapter adapter = adapter(record -> log.info("处理消息：{}", record.getMessageId()),
                acquiredChecker(lease), mock(KafkaConsumerErrorHandler.class), mock(DeadLetterPublisher.class), null);

        adapter.onManualCommitMessage(record(), acknowledgment);
        log.info("正常消费结果：leaseComplete=true，sourceAcknowledged=true");

        verify(lease).complete();
        verify(lease, never()).release();
        verify(acknowledgment).acknowledge();
        assertInvocationOrder(lease, acknowledgment);
    }

    @Test
    public void testCompletedMessageAcknowledgesWithoutInvokingHandler() throws Exception {
        KafkaConsumerHandler<String, String> handler = mock(KafkaConsumerHandler.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        List<KafkaConsumerEventContext> events = new ArrayList<>();

        adapter(handler, checker(KafkaConsumerIdempotencyAcquireResult.completed()), mock(KafkaConsumerErrorHandler.class),
                mock(DeadLetterPublisher.class), events::add).onManualCommitMessage(record(), acknowledgment);
        log.info("已完成幂等消息结果：eventCount={}，eventType={}", events.size(),
                events.isEmpty() ? null : events.get(0).getEventType());

        verify(handler, never()).handle(any(KafkaConsumerRecord.class));
        verify(acknowledgment).acknowledge();
        assertEquals(1, events.size(), "已完成消息必须只产生一次幂等拒绝事件");
        KafkaConsumerEventContext event = events.get(0);
        assertEquals(ConsumerEventType.IDEMPOTENT_REJECT, event.getEventType(), "完成标记必须按重复消息处理");
        assertEquals(TOPIC, event.getTopic(), "幂等拒绝事件必须保留 source topic");
        assertEquals(DATASOURCE, event.getDatasourceKey(), "幂等拒绝事件必须保留 datasource");
        assertEquals(1, event.getAttempt(), "幂等拒绝事件必须标记为首次消费");
        assertNull(event.getErrorCode(), "幂等拒绝不是消费错误");
    }

    @Test
    public void testInProgressMessageDoesNotAcknowledgeOrInvokeHandler() throws Exception {
        KafkaConsumerHandler<String, String> handler = mock(KafkaConsumerHandler.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                () -> adapter(handler, checker(KafkaConsumerIdempotencyAcquireResult.inProgress()),
                        mock(KafkaConsumerErrorHandler.class), mock(DeadLetterPublisher.class), null)
                        .onManualCommitMessage(record(), acknowledgment),
                "处理中租约必须阻止本次处理并等待 Kafka 重投");
        log.info("处理中租约结果：errorCode={}，message={}", exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.IDEMPOTENCY_IN_PROGRESS, exception.getErrorCode(), "处理中租约应返回等待恢复状态");
        verify(handler, never()).handle(any(KafkaConsumerRecord.class));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    public void testDeadLetterSuccessCompletesLeaseBeforeAcknowledgment() throws Exception {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.complete()).thenReturn(true);
        DeadLetterPublisher publisher = mock(DeadLetterPublisher.class);
        when(publisher.publish(any(KafkaConsumerRecord.class), any(Exception.class), anyInt(), anyString())).thenReturn(true);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        adapter(failingHandler(), acquiredChecker(lease), deadLetterErrorHandler(), publisher, null)
                .onManualCommitMessage(record(), acknowledgment);
        log.info("DLT 成功结果：leaseComplete=true，sourceAcknowledged=true");

        verify(lease).complete();
        verify(lease, never()).release();
        verify(acknowledgment).acknowledge();
        assertInvocationOrder(lease, acknowledgment);
    }

    @Test
    public void testDeadLetterFailureReleasesLeaseWithoutAcknowledging() {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.release()).thenReturn(false);
        DeadLetterPublisher publisher = mock(DeadLetterPublisher.class);
        when(publisher.publish(any(KafkaConsumerRecord.class), any(Exception.class), anyInt(), anyString())).thenReturn(false);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        List<KafkaConsumerEventContext> events = new ArrayList<>();

        KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                () -> adapter(failingHandler(), acquiredChecker(lease), deadLetterErrorHandler(), publisher, events::add)
                        .onManualCommitMessage(record(), acknowledgment),
                "DLT 失败时 source record 不能 ack");
        log.info("DLT 失败结果：errorCode={}，eventCount={}", exception.getErrorCode(), events.size());

        assertEquals(ErrorCode.DEAD_LETTER_PUBLISH_FAILED, exception.getErrorCode(), "应返回 DLT 投递失败错误");
        verify(lease).release();
        verify(lease, never()).complete();
        verify(acknowledgment, never()).acknowledge();
        assertEquals(ConsumerEventType.ERROR, events.get(0).getEventType(), "DLT 失败必须产生 ERROR 事件");
    }

    @Test
    public void testReleaseExceptionDoesNotAcknowledgeOrHideDeadLetterFailure() {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.release()).thenThrow(new IllegalStateException("mock release failure"));
        DeadLetterPublisher publisher = mock(DeadLetterPublisher.class);
        when(publisher.publish(any(KafkaConsumerRecord.class), any(Exception.class), anyInt(), anyString())).thenReturn(false);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        List<KafkaConsumerEventContext> events = new ArrayList<>();

        KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                () -> adapter(failingHandler(), acquiredChecker(lease), deadLetterErrorHandler(), publisher, events::add)
                        .onManualCommitMessage(record(), acknowledgment),
                "租约释放异常时 source record 不能 ack");
        log.info("租约释放异常结果：errorCode={}，eventCount={}", exception.getErrorCode(), events.size());

        assertEquals(ErrorCode.DEAD_LETTER_PUBLISH_FAILED, exception.getErrorCode(), "必须保留原始 DLT 失败语义");
        verify(lease).release();
        verify(lease, never()).complete();
        verify(acknowledgment, never()).acknowledge();
        assertEquals(1, events.size(), "租约释放异常不得吞掉 DLT 失败事件");
        assertEquals(ConsumerEventType.ERROR, events.get(0).getEventType());
        assertEquals(ErrorCode.DEAD_LETTER_PUBLISH_FAILED, events.get(0).getErrorCode());
    }

    @Test
    public void testDeadLetterPublisherExceptionReleasesLeaseWithoutAcknowledging() {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.release()).thenReturn(true);
        DeadLetterPublisher publisher = mock(DeadLetterPublisher.class);
        doThrow(new IllegalStateException("mock DLT failure")).when(publisher)
                .publish(any(KafkaConsumerRecord.class), any(Exception.class), anyInt(), anyString());
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        List<KafkaConsumerEventContext> events = new ArrayList<>();

        KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                () -> adapter(failingHandler(), acquiredChecker(lease), deadLetterErrorHandler(), publisher, events::add)
                        .onManualCommitMessage(record(), acknowledgment),
                "DLT 发布器抛异常时 source record 不能 ack");
        log.info("DLT 发布器异常结果：errorCode={}，eventCount={}", exception.getErrorCode(), events.size());

        assertEquals(ErrorCode.DEAD_LETTER_PUBLISH_FAILED, exception.getErrorCode());
        verify(lease).release();
        verify(lease, never()).complete();
        verify(acknowledgment, never()).acknowledge();
        assertEquals(1, events.size(), "DLT 发布器异常必须只产生一条 ERROR 事件");
        assertEquals(ConsumerEventType.ERROR, events.get(0).getEventType());
    }

    @Test
    public void testNullErrorHandlerDecisionFallsBackToDeadLetter() throws Exception {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.complete()).thenReturn(true);
        KafkaConsumerErrorHandler<String, String> errorHandler = (record, cause, attempt) -> null;
        DeadLetterPublisher publisher = mock(DeadLetterPublisher.class);
        when(publisher.publish(any(KafkaConsumerRecord.class), any(Exception.class), anyInt(), anyString())).thenReturn(true);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        List<KafkaConsumerEventContext> events = new ArrayList<>();

        adapter(failingHandler(), acquiredChecker(lease), errorHandler, publisher, events::add)
                .onManualCommitMessage(record(), acknowledgment);
        log.info("空错误处理决策结果：eventCount={}，eventType={}，errorCode={}", events.size(),
                events.isEmpty() ? null : events.get(0).getEventType(),
                events.isEmpty() ? null : events.get(0).getErrorCode());

        verify(publisher).publish(any(KafkaConsumerRecord.class), any(Exception.class), anyInt(),
                eq(ErrorCode.CONSUME_UNKNOWN));
        verify(lease).complete();
        verify(acknowledgment).acknowledge();
        assertEquals(1, events.size(), "非法错误处理决策必须只产生一条终态死信事件");
        assertEquals(ConsumerEventType.DEAD_LETTER, events.get(0).getEventType());
        assertEquals(ErrorCode.CONSUME_UNKNOWN, events.get(0).getErrorCode());
    }

    @Test
    public void testRetryBackoffInterruptionReleasesLeaseWithoutAcknowledging() {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.release()).thenReturn(true);
        KafkaConsumerErrorHandler<String, String> errorHandler = (record, cause, attempt) -> ErrorHandlerDecision.builder()
                .outcome(ErrorHandlerOutcome.RETRY).backoffMs(1000L).errorCode(ErrorCode.CONSUME_RETRYABLE)
                .retryable(true).build();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        try {
            Thread.currentThread().interrupt();
            KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                    () -> adapter(failingHandler(), acquiredChecker(lease), errorHandler, mock(DeadLetterPublisher.class), null)
                            .onManualCommitMessage(record(), acknowledgment),
                    "重试退避中断时不得提交 source offset");
            log.info("重试退避中断结果：errorCode={}，interrupted={}", exception.getErrorCode(),
                    Thread.currentThread().isInterrupted());

            assertEquals(ErrorCode.CONSUME_RETRYABLE, exception.getErrorCode());
            assertTrue(Thread.currentThread().isInterrupted(), "退避中断后必须恢复线程中断标志");
            verify(lease).release();
            verify(lease, never()).complete();
            verify(acknowledgment, never()).acknowledge();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testHandlerInterruptionReleasesLeaseAndPreservesInterrupt() {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.release()).thenReturn(true);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        KafkaConsumerHandler<String, String> handler = record -> {
            throw new InterruptedException("mock interruption");
        };

        try {
            KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                    () -> adapter(handler, acquiredChecker(lease), mock(KafkaConsumerErrorHandler.class),
                            mock(DeadLetterPublisher.class), null).onManualCommitMessage(record(), acknowledgment),
                    "handler 中断必须让 Kafka 重投");
            log.info("Handler 中断结果：errorCode={}，interrupted={}", exception.getErrorCode(),
                    Thread.currentThread().isInterrupted());

            assertEquals(ErrorCode.CONSUME_RETRYABLE, exception.getErrorCode(), "中断应保持可重投语义");
            assertTrue(Thread.currentThread().isInterrupted(), "handler 中断后必须恢复线程中断标志");
            verify(lease).release();
            verify(lease, never()).complete();
            verify(acknowledgment, never()).acknowledge();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void testCompletionFailureDoesNotAcknowledge() {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.complete()).thenReturn(false);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                () -> adapter(record -> {
                        }, acquiredChecker(lease), mock(KafkaConsumerErrorHandler.class),
                        mock(DeadLetterPublisher.class), null).onManualCommitMessage(record(), acknowledgment),
                "完成标记失败时不得提交 offset");
        log.info("完成标记失败结果：errorCode={}", exception.getErrorCode());

        assertEquals(ErrorCode.IDEMPOTENCY_CHECK_FAILED, exception.getErrorCode(), "完成标记失败应进入重投路径");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    public void testCompletionExceptionDoesNotAcknowledge() {
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.complete()).thenThrow(new IllegalStateException("mock complete failure"));
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                () -> adapter(record -> {
                        }, acquiredChecker(lease), mock(KafkaConsumerErrorHandler.class),
                        mock(DeadLetterPublisher.class), null).onManualCommitMessage(record(), acknowledgment),
                "完成标记抛异常时不得提交 offset");
        log.info("完成标记异常结果：errorCode={}", exception.getErrorCode());

        assertEquals(ErrorCode.IDEMPOTENCY_CHECK_FAILED, exception.getErrorCode());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    public void testRetryReusesOneLeaseThenCompletes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        KafkaConsumerIdempotencyLease lease = mock(KafkaConsumerIdempotencyLease.class);
        when(lease.complete()).thenReturn(true);
        KafkaConsumerErrorHandler<String, String> errorHandler = (record, cause, attempt) -> ErrorHandlerDecision.builder()
                .outcome(ErrorHandlerOutcome.RETRY).backoffMs(0L).errorCode(ErrorCode.CONSUME_RETRYABLE)
                .retryable(true).build();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        adapter(record -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("mock retry");
            }
        }, acquiredChecker(lease), errorHandler, mock(DeadLetterPublisher.class), null)
                .onManualCommitMessage(record(), acknowledgment);
        log.info("本地重试结果：handlerCalls={}，leaseComplete=true，sourceAcknowledged=true", calls.get());

        assertEquals(2, calls.get(), "本地重试必须复用当前租约并再次执行 handler");
        verify(lease).complete();
        verify(lease, never()).release();
        verify(acknowledgment).acknowledge();
    }

    @Test
    public void testMessageIdTrimsHeaderAndFallsBackToRecordIdentity() throws Exception {
        KafkaConsumerIdempotencyLease headerLease = mock(KafkaConsumerIdempotencyLease.class);
        when(headerLease.complete()).thenReturn(true);
        KafkaConsumerIdempotencyLease fallbackLease = mock(KafkaConsumerIdempotencyLease.class);
        when(fallbackLease.complete()).thenReturn(true);
        List<String> acquiredMessageIds = new ArrayList<>();
        KafkaConsumerIdempotencyChecker checker = (messageId, datasourceKey, groupId) -> {
            acquiredMessageIds.add(messageId);
            return acquiredMessageIds.size() == 1
                    ? KafkaConsumerIdempotencyAcquireResult.acquired(headerLease)
                    : KafkaConsumerIdempotencyAcquireResult.acquired(fallbackLease);
        };
        ConsumerRecord<String, String> withWhitespaceHeader = record();
        withWhitespaceHeader.headers().add(SimpleKafkaConsumerConstant.HEADER_MESSAGE_ID,
                " mock-message-id ".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ConsumerRecord<String, String> withoutHeader = new ConsumerRecord<>(TOPIC, 2, 7L, "mock-key", "mock-value");

        adapter(record -> {
        }, checker, mock(KafkaConsumerErrorHandler.class), mock(DeadLetterPublisher.class), null)
                .onManualCommitMessage(withWhitespaceHeader, mock(Acknowledgment.class));
        adapter(record -> {
        }, checker, mock(KafkaConsumerErrorHandler.class), mock(DeadLetterPublisher.class), null)
                .onManualCommitMessage(withoutHeader, mock(Acknowledgment.class));

        log.info("解析后的 messageId：header={}，fallback={}", acquiredMessageIds.get(0), acquiredMessageIds.get(1));
        assertEquals("mock-message-id", acquiredMessageIds.get(0), "messageId header 必须去除首尾空白");
        assertEquals("mock-topic:2:7", acquiredMessageIds.get(1), "缺失 messageId 时必须回退到 topic:partition:offset");
    }

    private KafkaConsumerHandler<String, String> failingHandler() {
        return record -> {
            throw new IllegalArgumentException("mock failure");
        };
    }

    private KafkaConsumerIdempotencyChecker acquiredChecker(KafkaConsumerIdempotencyLease lease) {
        return checker(KafkaConsumerIdempotencyAcquireResult.acquired(lease));
    }

    private KafkaConsumerIdempotencyChecker checker(KafkaConsumerIdempotencyAcquireResult result) {
        return (messageId, datasourceKey, groupId) -> result;
    }

    private KafkaConsumerHandlerAdapter adapter(KafkaConsumerHandler<String, String> handler,
                                                KafkaConsumerIdempotencyChecker checker,
                                                KafkaConsumerErrorHandler<String, String> errorHandler,
                                                DeadLetterPublisher deadLetterPublisher,
                                                KafkaConsumerEventListener eventListener) {
        return new KafkaConsumerHandlerAdapter(handler, checker, errorHandler, deadLetterPublisher, eventListener,
                DATASOURCE, GROUP);
    }

    private KafkaConsumerErrorHandler<String, String> deadLetterErrorHandler() {
        return (record, cause, attempt) -> ErrorHandlerDecision.builder()
                .outcome(ErrorHandlerOutcome.DEAD_LETTER).backoffMs(0L).errorCode(ErrorCode.CONSUME_FATAL)
                .retryable(false).build();
    }

    private ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>(TOPIC, 0, 0L, "mock-key", "mock-value");
    }

    private void assertInvocationOrder(KafkaConsumerIdempotencyLease lease, Acknowledgment acknowledgment) {
        org.mockito.InOrder order = inOrder(lease, acknowledgment);
        order.verify(lease).complete();
        order.verify(acknowledgment).acknowledge();
    }
}
