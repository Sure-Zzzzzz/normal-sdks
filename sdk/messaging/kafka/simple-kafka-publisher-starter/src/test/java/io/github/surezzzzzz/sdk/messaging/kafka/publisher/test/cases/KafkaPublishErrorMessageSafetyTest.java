package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.KafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kafka Publisher 错误消息安全测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublishErrorMessageSafetyTest {

    private static final String UNSAFE_TOPIC = "mock\nforged-topic";
    private static final String UNSAFE_MESSAGE_TYPE =
            "mock" + (char) 0x2028 + "forged-type";
    private static final String UNSAFE_MESSAGE_ID = repeat('a',
            SimpleKafkaPublisherConstant.MAX_ERROR_DISPLAY_LENGTH + 1);

    @Test
    public void testSerializeFailureSanitizesDynamicMetadata() {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        DefaultKafkaPublisher publisher = publisher(routeTemplate, context -> null, 3000L);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(unsafeMessage()));

        log.info("序列化失败安全错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_006, exception.getErrorCode(),
                "序列化失败错误码应保持不变");
        assertSanitized(exception.getMessage(), 2);
        verifyNoInteractions(routeTemplate);
    }

    @Test
    public void testSendFailureSanitizesDynamicMetadata() throws Exception {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        when(routeTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new IllegalStateException("mock-send-failure"));
        DefaultKafkaPublisher publisher = publisher(routeTemplate,
                context -> KafkaPublisherTestHelper.PAYLOAD, 3000L);

        ExecutionException executionException = assertThrows(ExecutionException.class,
                () -> publisher.publish(unsafeMessage()).get(3L, TimeUnit.SECONDS));
        Throwable cause = executionException.getCause();

        assertNotNull(cause, "发送失败的 ExecutionException 应保留 cause");
        assertTrue(cause instanceof KafkaPublishException,
                "发送失败应通过 Future 传播 KafkaPublishException");
        KafkaPublishException exception = (KafkaPublishException) cause;
        log.info("发送失败安全错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_007, exception.getErrorCode(),
                "发送失败错误码应保持不变");
        assertSanitized(exception.getMessage(), 3);
    }

    @Test
    public void testTimeoutSanitizesDynamicMetadata() {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        when(routeTemplate.send(any(ProducerRecord.class)))
                .thenReturn(new SettableListenableFuture<SendResult<String, String>>());
        DefaultKafkaPublisher publisher = publisher(routeTemplate,
                context -> KafkaPublisherTestHelper.PAYLOAD, 1L);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publishAndWait(unsafeMessage()));

        log.info("同步超时安全错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_008, exception.getErrorCode(),
                "同步超时错误码应保持不变");
        assertSanitized(exception.getMessage(), 3);
    }

    @Test
    public void testInterruptSanitizesDynamicMetadata() throws Exception {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        when(routeTemplate.send(any(ProducerRecord.class)))
                .thenReturn(new SettableListenableFuture<SendResult<String, String>>());
        DefaultKafkaPublisher publisher = publisher(routeTemplate,
                context -> KafkaPublisherTestHelper.PAYLOAD, 60000L);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                publisher.publishAndWait(unsafeMessage());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            }
        });
        worker.start();
        long deadline = System.currentTimeMillis() + 2000L;
        while (worker.getState() != Thread.State.TIMED_WAITING
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }

        assertEquals(Thread.State.TIMED_WAITING, worker.getState(),
                "worker 应进入同步等待状态后再触发中断");
        worker.interrupt();
        worker.join(2000L);
        Throwable error = errorRef.get();

        assertFalse(worker.isAlive(), "中断后 worker 应退出");
        assertNotNull(error, "同步等待中断应抛出异常");
        assertTrue(error instanceof KafkaPublishException,
                "同步等待中断应抛 KafkaPublishException");
        KafkaPublishException exception = (KafkaPublishException) error;
        log.info("同步中断安全错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_011, exception.getErrorCode(),
                "同步中断错误码应保持不变");
        assertSanitized(exception.getMessage(), 3);
    }

    private DefaultKafkaPublisher publisher(KafkaRouteTemplate routeTemplate,
                                             KafkaPublishSerializer serializer,
                                             long timeoutMs) {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getSend().setTimeoutMs(timeoutMs);
        return new DefaultKafkaPublisher(routeTemplate, properties, serializer,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver(),
                new DefaultKafkaPublishRouteKeyResolver(),
                () -> KafkaPublisherTestHelper.MESSAGE_ID,
                () -> KafkaPublisherTestHelper.TRACE_ID,
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Collections.emptyList(), Collections.emptyList());
    }

    private KafkaPublishMessage<String> unsafeMessage() {
        return KafkaPublishMessage.<String>builder()
                .topic(UNSAFE_TOPIC)
                .messageId(UNSAFE_MESSAGE_ID)
                .messageType(UNSAFE_MESSAGE_TYPE)
                .payload(KafkaPublisherTestHelper.PAYLOAD)
                .envelopeEnabled(false)
                .build();
    }

    private void assertSanitized(String message, int expectedPlaceholderCount) {
        assertEquals(expectedPlaceholderCount,
                countOccurrences(message, SimpleKafkaPublisherConstant.ERROR_VALUE_UNSAFE_DISPLAY),
                "每个不安全动态字段都应独立替换为固定占位符");
        assertFalse(message.contains("forged-topic"), "错误消息不应回显不安全 topic");
        assertFalse(message.contains("forged-type"), "错误消息不应回显不安全 messageType");
        assertFalse(message.contains(UNSAFE_MESSAGE_ID), "错误消息不应回显超长 messageId");
    }

    private int countOccurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
