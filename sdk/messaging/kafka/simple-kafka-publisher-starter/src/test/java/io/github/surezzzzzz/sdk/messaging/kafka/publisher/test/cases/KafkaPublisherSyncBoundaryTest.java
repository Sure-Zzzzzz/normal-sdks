package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kafka Publisher 同步边界测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublisherSyncBoundaryTest {

    @Test
    public void testAsyncPublishDoesNotReadOrApplyWaitTimeout() throws Exception {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        SettableListenableFuture<SendResult<String, String>> sendFuture = new SettableListenableFuture<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenReturn(sendFuture);
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getSend().setTimeoutMs(1L);
        DefaultKafkaPublisher publisher = publisher(routeTemplate, properties);

        ListenableFuture<?> resultFuture = publisher.publish(KafkaPublisherTestHelper.message());

        Thread.sleep(20L);
        log.info("异步发布超过配置 timeout 后 Future 状态: {}", resultFuture.isDone());
        assertFalse(resultFuture.isDone(),
                "异步 publish 超过 send.timeout-ms 后仍不应自动超时、阻塞或完成");
    }

    @Test
    public void testPublishAndWaitUsesConfiguredTimeoutAndDoesNotLeak() {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        SettableListenableFuture<SendResult<String, String>> sendFuture = new SettableListenableFuture<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenReturn(sendFuture);
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getSend().setTimeoutMs(1L);
        DefaultKafkaPublisher publisher = publisher(routeTemplate, properties);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publishAndWait(KafkaPublisherTestHelper.message()));

        log.info("同步等待超时错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_008, exception.getErrorCode(),
                "同步等待超时应使用明确错误码");
        assertTrue(exception.getMessage().contains("发送状态未知"),
                "同步等待超时应明确提示发送状态未知");
        assertTrue(exception.getMessage().contains("不应盲目重试"),
                "同步等待超时应提示调用方避免盲目重试造成重复投递");
        assertFalse(exception.getMessage().contains(KafkaPublisherTestHelper.KEY),
                "超时错误消息不应泄漏 key");
        assertFalse(exception.getMessage().contains(KafkaPublisherTestHelper.PAYLOAD),
                "超时错误消息不应泄漏 payload");
    }

    @Test
    public void testPublishAndWaitReturnsResultOnSuccess() throws Exception {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation ->
                KafkaPublisherTestHelper.successFuture(invocation.getArgument(0)));
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        DefaultKafkaPublisher publisher = publisher(routeTemplate, properties);

        KafkaPublishResult result = publisher.publishAndWait(KafkaPublisherTestHelper.message());

        log.info("同步发布成功结果: {}", result);
        assertEquals(KafkaPublisherTestHelper.TOPIC, result.getTopic(), "同步结果 topic 应来自 metadata");
        assertEquals(KafkaPublisherTestHelper.MESSAGE_ID, result.getMessageId(),
                "同步结果 messageId 应精确一致");
        assertEquals(KafkaPublisherTestHelper.OFFSET, result.getOffset(), "同步结果 offset 应来自 metadata");
        assertEquals(KafkaPublisherTestHelper.PARTITION, result.getPartition(),
                "同步结果 partition 应来自 metadata");
    }

    @Test
    public void testPublishAndWaitUnwrapsKafkaPublishExceptionFromExecutionException() {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        SettableListenableFuture<SendResult<String, String>> failedFuture = new SettableListenableFuture<>();
        failedFuture.setException(new RuntimeException("mock broker failure"));
        when(routeTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        DefaultKafkaPublisher publisher = publisher(routeTemplate, properties);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publishAndWait(KafkaPublisherTestHelper.message()));

        log.info("同步发布失败错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_007, exception.getErrorCode(),
                "同步发布底层失败应解包为发送失败错误码");
        assertTrue(exception.getCause() instanceof RuntimeException,
                "原始 cause 应保留");
    }

    @Test
    public void testPublishAndWaitInterruptedReportsSendInterrupted() throws Exception {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        SettableListenableFuture<SendResult<String, String>> sendFuture = new SettableListenableFuture<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenReturn(sendFuture);
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getSend().setTimeoutMs(60_000L);
        DefaultKafkaPublisher publisher = publisher(routeTemplate, properties);

        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Boolean> interruptedRef =
                new java.util.concurrent.atomic.AtomicReference<>(Boolean.FALSE);
        Thread worker = new Thread(() -> {
            try {
                publisher.publishAndWait(KafkaPublisherTestHelper.message());
            } catch (Throwable t) {
                errorRef.set(t);
                interruptedRef.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        // 自旋等待 worker 进入 publishAndWait 的同步等待阻塞
        long deadline = System.currentTimeMillis() + 2_000L;
        while (worker.getState() != Thread.State.TIMED_WAITING
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertEquals(Thread.State.TIMED_WAITING, worker.getState(),
                "worker 应阻塞在 publishAndWait 的同步等待上");
        worker.interrupt();
        worker.join(2_000L);

        Throwable error = errorRef.get();
        log.info("中断错误: {}", error == null ? null : error.getMessage());
        assertFalse(worker.isAlive(), "中断后 worker 应在 join 超时前退出");
        assertNotNull(error, "同步等待被中断应抛出异常");
        assertTrue(error instanceof KafkaPublishException, "应抛 KafkaPublishException");
        KafkaPublishException exception = (KafkaPublishException) error;
        assertEquals(ErrorCode.KAFKA_PUBLISHER_011, exception.getErrorCode(),
                "中断应使用 011 错误码，不应误报发送失败诱发重试");
        assertTrue(exception.getCause() instanceof InterruptedException,
                "cause 应为 InterruptedException");
        assertEquals(Boolean.TRUE, interruptedRef.get(),
                "publisher 捕获 InterruptedException 后应恢复 worker 的中断标记");
    }

    private DefaultKafkaPublisher publisher(KafkaRouteTemplate routeTemplate,
                                            SimpleKafkaPublisherProperties properties) {
        return new DefaultKafkaPublisher(routeTemplate, properties,
                new JacksonKafkaPublishSerializer(),
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver(),
                new DefaultKafkaPublishRouteKeyResolver(),
                () -> KafkaPublisherTestHelper.MESSAGE_ID,
                () -> KafkaPublisherTestHelper.TRACE_ID,
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Collections.emptyList(), Collections.emptyList());
    }
}
