package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 默认 Kafka Publisher 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaPublisherTest {

    private KafkaRouteTemplate routeTemplate;
    private DefaultKafkaPublisher publisher;

    @BeforeEach
    public void setUp() {
        routeTemplate = mock(KafkaRouteTemplate.class);
        publisher = KafkaPublisherTestHelper.publisher(routeTemplate);
    }

    @Test
    public void testPublishByTopicPreservesApiInput() throws Exception {
        AtomicReference<ProducerRecord<String, String>> recordRef = new AtomicReference<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            recordRef.set(record);
            return KafkaPublisherTestHelper.successFuture(record);
        });

        KafkaPublishResult result = publisher.publish(KafkaPublisherTestHelper.API_TOPIC,
                        KafkaPublisherTestHelper.API_KEY, KafkaPublisherTestHelper.PAYLOAD)
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ProducerRecord<String, String> record = recordRef.get();

        log.info("topic 发布 record: {}", record);
        log.info("topic 发布结果: {}", result);
        assertNotNull(record, KafkaPublisherTestHelper.ASSERT_RECORD_MESSAGE);
        assertEquals(KafkaPublisherTestHelper.API_TOPIC, record.topic(), "API topic 应直接用于本次发送");
        assertEquals(KafkaPublisherTestHelper.API_KEY, record.key(), "API key 应直接用于本次发送");
        assertEquals(KafkaPublisherTestHelper.PAYLOAD, record.value(), "String payload 不应被 JSON quote");
        assertEquals(KafkaPublisherTestHelper.API_TOPIC, result.getTopic(), "结果 topic 应来自 metadata");
        verify(routeTemplate).send(any(ProducerRecord.class));
        verify(routeTemplate, never()).sendByRouteKey(anyString(), any(ProducerRecord.class));
        verify(routeTemplate, never()).sendOn(anyString(), any(ProducerRecord.class));
    }

    @Test
    public void testPublishMessageUsesDatasourceBeforeRouteKey() throws Exception {
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setDatasourceKey(KafkaPublisherTestHelper.DATASOURCE_KEY);
        message.setRouteKey(KafkaPublisherTestHelper.ROUTE_KEY);
        when(routeTemplate.sendOn(eq(KafkaPublisherTestHelper.DATASOURCE_KEY), any(ProducerRecord.class)))
                .thenAnswer(invocation -> KafkaPublisherTestHelper.successFuture(invocation.getArgument(1)));

        KafkaPublishResult result = publisher.publish(message)
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("datasource 发布结果: {}", result);
        assertNull(result.getDatasourceKey(), "publish(message) 虽按字段显式 datasource，结果仅 publishOn API 回填 datasourceKey");
        assertEquals(KafkaPublisherTestHelper.ROUTE_KEY, message.getRouteKey(), "发送过程不应回写 message");
        verify(routeTemplate).sendOn(eq(KafkaPublisherTestHelper.DATASOURCE_KEY), any(ProducerRecord.class));
        verify(routeTemplate, never()).sendByRouteKey(anyString(), any(ProducerRecord.class));
    }

    @Test
    public void testPublishOnReturnsExplicitDatasource() throws Exception {
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setRouteKey(KafkaPublisherTestHelper.ROUTE_KEY);
        when(routeTemplate.sendOn(eq(KafkaPublisherTestHelper.DATASOURCE_KEY), any(ProducerRecord.class)))
                .thenAnswer(invocation -> KafkaPublisherTestHelper.successFuture(invocation.getArgument(1)));

        KafkaPublishResult result = publisher.publishOn(KafkaPublisherTestHelper.DATASOURCE_KEY, message)
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("显式 datasource 发布结果: {}", result);
        assertEquals(KafkaPublisherTestHelper.DATASOURCE_KEY, result.getDatasourceKey(),
                "publishOn 结果应回填显式 datasourceKey");
        verify(routeTemplate).sendOn(eq(KafkaPublisherTestHelper.DATASOURCE_KEY), any(ProducerRecord.class));
        verify(routeTemplate, never()).sendByRouteKey(anyString(), any(ProducerRecord.class));
    }

    @Test
    public void testPublishByRouteKeyIgnoresMessageDatasource() throws Exception {
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setDatasourceKey(KafkaPublisherTestHelper.DATASOURCE_KEY);
        when(routeTemplate.sendByRouteKey(eq(KafkaPublisherTestHelper.ROUTE_KEY), any(ProducerRecord.class)))
                .thenAnswer(invocation -> KafkaPublisherTestHelper.successFuture(invocation.getArgument(1)));

        KafkaPublishResult result = publisher.publishByRouteKey(KafkaPublisherTestHelper.ROUTE_KEY, message)
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("routeKey 发布结果: {}", result);
        assertNull(result.getDatasourceKey(), "routeKey 模式结果 datasourceKey 应为空");
        verify(routeTemplate).sendByRouteKey(eq(KafkaPublisherTestHelper.ROUTE_KEY), any(ProducerRecord.class));
        verify(routeTemplate, never()).sendOn(anyString(), any(ProducerRecord.class));
    }

    @Test
    public void testProducerRecordPreservesPartitionTimestampAndHeader() throws Exception {
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setPartition(KafkaPublisherTestHelper.PARTITION);
        message.setTimestamp(KafkaPublisherTestHelper.RECORD_TIMESTAMP);
        message.setHeaders(Collections.singletonMap(KafkaPublisherTestHelper.CUSTOM_HEADER,
                KafkaPublisherTestHelper.CUSTOM_HEADER_VALUE));
        AtomicReference<ProducerRecord<String, String>> recordRef = new AtomicReference<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            recordRef.set(record);
            return KafkaPublisherTestHelper.successFuture(record);
        });

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ProducerRecord<String, String> record = recordRef.get();

        log.info("带分区、时间戳和 header 的 record: {}", record);
        assertEquals(KafkaPublisherTestHelper.PARTITION, record.partition(), "partition 应保留");
        assertEquals(KafkaPublisherTestHelper.RECORD_TIMESTAMP, record.timestamp(), "timestamp 应保留");
        assertArrayEquals(KafkaPublisherTestHelper.CUSTOM_HEADER_VALUE.getBytes(StandardCharsets.UTF_8),
                record.headers().lastHeader(KafkaPublisherTestHelper.CUSTOM_HEADER).value(), "header 应按 UTF-8 编码");
    }

    @Test
    public void testBlankRouteInputRejected() {
        KafkaPublishException routeException = assertThrows(KafkaPublishException.class,
                () -> publisher.publishByRouteKey(" ", KafkaPublisherTestHelper.message()));
        KafkaPublishException datasourceException = assertThrows(KafkaPublishException.class,
                () -> publisher.publishOn(" ", KafkaPublisherTestHelper.message()));

        log.info("blank routeKey 错误码: {}", routeException.getErrorCode());
        log.info("blank datasourceKey 错误码: {}", datasourceException.getErrorCode());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_010, routeException.getErrorCode(), "blank routeKey 错误码应正确");
        assertEquals(ErrorCode.KAFKA_PUBLISHER_010, datasourceException.getErrorCode(),
                "blank datasourceKey 错误码应正确");
    }

    @Test
    public void testRouteResolverOnlyRunsForAutoModeWithoutDatasource() throws Exception {
        io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties properties =
                KafkaPublisherTestHelper.properties();
        io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishRouteKeyResolver resolver =
                mock(io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishRouteKeyResolver.class);
        when(resolver.resolveRouteKey(any(KafkaPublishMessage.class)))
                .thenReturn(KafkaPublisherTestHelper.ROUTE_KEY);
        DefaultKafkaPublisher publisherWithResolver = new DefaultKafkaPublisher(routeTemplate, properties,
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer(
                        new com.fasterxml.jackson.databind.ObjectMapper()),
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver(),
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver(),
                resolver,
                () -> KafkaPublisherTestHelper.MESSAGE_ID,
                () -> KafkaPublisherTestHelper.TRACE_ID,
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Collections.emptyList(), Collections.emptyList());
        when(routeTemplate.sendByRouteKey(eq(KafkaPublisherTestHelper.ROUTE_KEY), any(ProducerRecord.class)))
                .thenAnswer(invocation -> KafkaPublisherTestHelper.successFuture(invocation.getArgument(1)));
        when(routeTemplate.sendOn(eq(KafkaPublisherTestHelper.DATASOURCE_KEY), any(ProducerRecord.class)))
                .thenAnswer(invocation -> KafkaPublisherTestHelper.successFuture(invocation.getArgument(1)));
        when(routeTemplate.send(any(ProducerRecord.class)))
                .thenAnswer(invocation -> KafkaPublisherTestHelper.successFuture(invocation.getArgument(0)));

        publisherWithResolver.publish(KafkaPublisherTestHelper.message())
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        KafkaPublishMessage<String> datasourceMessage = KafkaPublisherTestHelper.message();
        datasourceMessage.setDatasourceKey(KafkaPublisherTestHelper.DATASOURCE_KEY);
        publisherWithResolver.publish(datasourceMessage)
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        publisherWithResolver.publishByRouteKey(KafkaPublisherTestHelper.ROUTE_KEY,
                        KafkaPublisherTestHelper.message())
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        publisherWithResolver.publishOn(KafkaPublisherTestHelper.DATASOURCE_KEY,
                        KafkaPublisherTestHelper.message())
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        publisherWithResolver.publish(KafkaPublisherTestHelper.TOPIC, KafkaPublisherTestHelper.PAYLOAD)
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("routeKey resolver 调用边界验证完成");
        verify(resolver, times(1)).resolveRouteKey(any(KafkaPublishMessage.class));
    }

    @Test
    public void testGeneratedMessageIdCannotBeBlank() {
        io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties properties =
                KafkaPublisherTestHelper.properties();
        DefaultKafkaPublisher publisherWithBlankGenerator = new DefaultKafkaPublisher(routeTemplate, properties,
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer(
                        new com.fasterxml.jackson.databind.ObjectMapper()),
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver(),
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver(),
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver(),
                () -> " ", () -> KafkaPublisherTestHelper.TRACE_ID,
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Collections.emptyList(), Collections.emptyList());
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setMessageId(null);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisherWithBlankGenerator.publish(message));

        log.info("空 messageId 生成结果错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_002, exception.getErrorCode(),
                "空 messageId 生成结果应作为消息非法拒绝");
        verifyNoInteractions(routeTemplate);
    }

    @Test
    public void testEnvelopeCustomizerCannotMutateOriginalAttributes() throws Exception {
        io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties properties =
                KafkaPublisherTestHelper.properties();
        properties.getEnvelope().setEnable(true);
        AtomicReference<ProducerRecord<String, String>> recordRef = new AtomicReference<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            recordRef.set(record);
            return KafkaPublisherTestHelper.successFuture(record);
        });
        io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishEnvelopeCustomizer customizer =
                context -> context.getAttributes().put("customized", Boolean.TRUE);
        DefaultKafkaPublisher publisherWithCustomizer = new DefaultKafkaPublisher(routeTemplate, properties,
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer(
                        new com.fasterxml.jackson.databind.ObjectMapper()),
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver(),
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver(),
                new io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver(),
                () -> KafkaPublisherTestHelper.MESSAGE_ID, () -> KafkaPublisherTestHelper.TRACE_ID,
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Collections.emptyList(), Collections.singletonList(customizer));
        Map<String, Object> originalAttributes = new LinkedHashMap<>();
        originalAttributes.put("original", Boolean.TRUE);
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setAttributes(originalAttributes);
        message.setEnvelopeEnabled(true);

        publisherWithCustomizer.publish(message)
                .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("customizer 后原 attributes: {}", originalAttributes.keySet());
        assertEquals(Collections.singletonMap("original", Boolean.TRUE), originalAttributes,
                "customizer 不应回写调用方原始 attributes map");
        assertTrue(recordRef.get().value().contains("customized"),
                "customizer 补充的 attribute 应进入本次 envelope");
    }

    @Test
    public void testReturnedFutureCancellationPropagates() {
        org.springframework.util.concurrent.SettableListenableFuture<SendResult<String, String>> sendFuture =
                spy(new org.springframework.util.concurrent.SettableListenableFuture<>());
        when(routeTemplate.send(any(ProducerRecord.class))).thenReturn(sendFuture);

        ListenableFuture<KafkaPublishResult> resultFuture = publisher.publish(KafkaPublisherTestHelper.message());
        boolean cancelled = resultFuture.cancel(false);

        log.info("发布 Future 取消结果: {}", cancelled);
        assertTrue(cancelled, "包装 Future 应允许取消");
        assertTrue(resultFuture.isCancelled(), "包装 Future 应进入取消状态");
        verify(sendFuture).cancel(false);
    }

    @Test
    public void testNullSendFutureBecomesTypedFailure() {
        when(routeTemplate.send(any(ProducerRecord.class))).thenReturn(null);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> publisher.publish(KafkaPublisherTestHelper.message())
                        .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        log.info("空底层 Future 错误: {}", exception.getCause().getMessage());
        assertTrue(exception.getCause() instanceof KafkaPublishException,
                "空底层 Future 应包装为 KafkaPublishException");
        assertEquals(ErrorCode.KAFKA_PUBLISHER_007,
                ((KafkaPublishException) exception.getCause()).getErrorCode(), "错误码应为发送失败");
    }

    @Test
    public void testNullSendResultBecomesTypedFailure() {
        org.springframework.util.concurrent.SettableListenableFuture<SendResult<String, String>> sendFuture =
                new org.springframework.util.concurrent.SettableListenableFuture<>();
        sendFuture.set(null);
        when(routeTemplate.send(any(ProducerRecord.class))).thenReturn(sendFuture);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> publisher.publish(KafkaPublisherTestHelper.message())
                        .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        log.info("空底层发送结果错误: {}", exception.getCause().getMessage());
        assertTrue(exception.getCause() instanceof KafkaPublishException,
                "空底层发送结果应包装为 KafkaPublishException");
        assertEquals(ErrorCode.KAFKA_PUBLISHER_007,
                ((KafkaPublishException) exception.getCause()).getErrorCode(), "错误码应为发送失败");
    }

    @Test
    public void testSendFailurePropagatesThroughFuture() {
        RuntimeException rootCause = new RuntimeException("mock send failure");
        ListenableFuture<SendResult<String, String>> failedFuture = mock(ListenableFuture.class);
        doAnswer(invocation -> {
            org.springframework.util.concurrent.ListenableFutureCallback<SendResult<String, String>> callback =
                    invocation.getArgument(0);
            callback.onFailure(rootCause);
            return null;
        }).when(failedFuture).addCallback(any(org.springframework.util.concurrent.ListenableFutureCallback.class));
        when(routeTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

        Exception exception = assertThrows(Exception.class,
                () -> publisher.publish(KafkaPublisherTestHelper.message())
                        .get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        log.info("异步发送失败: {}", exception.getMessage());
        assertTrue(exception.getCause() instanceof KafkaPublishException, "future cause 应为 KafkaPublishException");
        assertEquals(ErrorCode.KAFKA_PUBLISHER_007,
                ((KafkaPublishException) exception.getCause()).getErrorCode(), "发送失败错误码应正确");
    }
}
