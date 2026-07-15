package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishHeaderCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishHeaderHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kafka 发布 header 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublishHeaderTest {

    private KafkaRouteTemplate routeTemplate;
    private SimpleKafkaPublisherProperties properties;

    @BeforeEach
    public void setUp() {
        routeTemplate = mock(KafkaRouteTemplate.class);
        properties = KafkaPublisherTestHelper.properties();
    }

    @Test
    public void testReservedHeaderCannotBeOverriddenCaseInsensitively() {
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setHeaders(Collections.singletonMap("X-Message-Id", "override-value"));
        DefaultKafkaPublisher publisher = publisher(Collections.emptyList());

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(message));

        log.info("保留 header 覆盖错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_009, exception.getErrorCode(), "错误码应为 header 非法");
        assertFalse(exception.getMessage().contains("override-value"), "错误消息不应包含 header value");
    }

    @Test
    public void testNullHeaderValueRejectedWithoutLeak() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(KafkaPublisherTestHelper.CUSTOM_HEADER, null);
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setHeaders(headers);
        DefaultKafkaPublisher publisher = publisher(Collections.emptyList());

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(message));

        log.info("null header value 错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_009, exception.getErrorCode(), "错误码应为 header 非法");
        assertTrue(exception.getMessage().contains(KafkaPublisherTestHelper.CUSTOM_HEADER),
                "错误消息应包含 header key");
    }

    @Test
    public void testCustomizerCannotRemoveReservedHeaderByDefault() {
        KafkaPublishHeaderCustomizer customizer = context ->
                context.getHeaders().remove(SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_ID);
        DefaultKafkaPublisher publisher = publisher(Collections.singletonList(customizer));

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(KafkaPublisherTestHelper.message()));

        log.info("customizer 删除默认 header 错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_009, exception.getErrorCode(),
                "删除默认 header 应被统一校验拒绝");
    }

    @Test
    public void testHeaderNameIsTrimmedBeforeRecordCreation() throws Exception {
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setHeaders(Collections.singletonMap("  " + KafkaPublisherTestHelper.CUSTOM_HEADER + "  ",
                KafkaPublisherTestHelper.CUSTOM_HEADER_VALUE));
        java.util.concurrent.atomic.AtomicReference<ProducerRecord<String, String>> recordRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            recordRef.set(record);
            return KafkaPublisherTestHelper.successFuture(record);
        });
        DefaultKafkaPublisher publisher = publisher(Collections.emptyList());

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);
        ProducerRecord<String, String> record = recordRef.get();

        log.info("trim 后 header: {}", record.headers().lastHeader(KafkaPublisherTestHelper.CUSTOM_HEADER));
        assertNotNull(record.headers().lastHeader(KafkaPublisherTestHelper.CUSTOM_HEADER),
                "最终 ProducerRecord 应使用 trim 后的 header 名称");
        assertNull(record.headers().lastHeader("  " + KafkaPublisherTestHelper.CUSTOM_HEADER + "  "),
                "最终 ProducerRecord 不应保留 header 名称两侧空格");
    }

    @Test
    public void testAllowHeaderOverrideCaseInsensitively() throws Exception {
        properties.getHeaders().setAllowHeaderOverride(true);
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setHeaders(Collections.singletonMap("X-Message-Id", "override-value"));
        java.util.concurrent.atomic.AtomicReference<ProducerRecord<String, String>> recordRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            recordRef.set(record);
            return KafkaPublisherTestHelper.successFuture(record);
        });
        DefaultKafkaPublisher publisher = publisher(Collections.emptyList());

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);
        ProducerRecord<String, String> record = recordRef.get();

        log.info("大小写覆盖后的 messageId header: {}", record.headers().lastHeader("X-Message-Id"));
        assertEquals(1, countHeadersIgnoreCase(record, SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_ID),
                "显式允许覆盖后大小写变体也只能保留一个 header");
        assertArrayEquals("override-value".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                record.headers().lastHeader("X-Message-Id").value(), "覆盖后的 header value 应生效");
    }

    @Test
    public void testDefaultHeaderNameCanBeUsedWhenDefaultHeadersDisabled() throws Exception {
        properties.getHeaders().setEnableDefaultHeaders(false);
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setHeaders(Collections.singletonMap(
                SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_ID, "custom-message-id"));
        java.util.concurrent.atomic.AtomicReference<ProducerRecord<String, String>> recordRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            recordRef.set(record);
            return KafkaPublisherTestHelper.successFuture(record);
        });
        DefaultKafkaPublisher publisher = publisher(Collections.emptyList());

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        log.info("默认 header 关闭后的同名业务 header: {}",
                recordRef.get().headers().lastHeader(SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_ID));
        assertTrue(KafkaPublishHeaderHelper.reservedHeaders(properties).isEmpty(),
                "默认 header 关闭后保留 header 集合应为空");
        assertFalse(KafkaPublishHeaderHelper.isReservedHeader(
                        SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_ID, properties),
                "默认 header 关闭后默认名称不应继续被判定为保留 header");
        assertArrayEquals("custom-message-id".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                recordRef.get().headers().lastHeader(
                        SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_ID).value(),
                "默认 header 关闭后，同名 header 应作为普通业务 header 发送");
    }

    @Test
    public void testCaseInsensitiveDuplicateHeaderReportsDuplicateReason() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Duplicate-Header", "value-1");
        headers.put("x-duplicate-header", "value-2");
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setHeaders(headers);
        DefaultKafkaPublisher publisher = publisher(Collections.emptyList());

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(message));

        log.info("大小写重复 header 错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_009, exception.getErrorCode(), "错误码应为 header 非法");
        assertTrue(exception.getMessage().contains(SimpleKafkaPublisherConstant.REASON_HEADER_DUPLICATE),
                "错误消息应标明大小写不敏感重复，而非误报默认 header 被覆盖");
    }

    private int countHeadersIgnoreCase(ProducerRecord<String, String> record, String headerName) {
        int count = 0;
        for (org.apache.kafka.common.header.Header header : record.headers()) {
            if (header.key().equalsIgnoreCase(headerName)) {
                count++;
            }
        }
        return count;
    }

    private DefaultKafkaPublisher publisher(java.util.List<KafkaPublishHeaderCustomizer> customizers) {
        return new DefaultKafkaPublisher(routeTemplate, properties,
                new JacksonKafkaPublishSerializer(new ObjectMapper()),
                new DefaultKafkaPublishTopicResolver(),
                new DefaultKafkaPublishKeyResolver(),
                new DefaultKafkaPublishRouteKeyResolver(),
                () -> KafkaPublisherTestHelper.MESSAGE_ID,
                () -> KafkaPublisherTestHelper.TRACE_ID,
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                customizers,
                Collections.emptyList());
    }
}
