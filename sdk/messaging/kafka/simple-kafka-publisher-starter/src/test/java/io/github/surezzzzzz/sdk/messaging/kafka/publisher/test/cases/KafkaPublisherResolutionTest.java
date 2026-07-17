package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kafka Publisher 解析与校验测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublisherResolutionTest {

    private KafkaRouteTemplate routeTemplate;

    @BeforeEach
    public void setUp() {
        routeTemplate = mock(KafkaRouteTemplate.class);
    }

    @Test
    public void testDefaultTopicFallbackWhenResolverReturnsNull() throws Exception {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.setDefaultTopic("mock.default.topic");
        DefaultKafkaPublisher publisher = publisher(properties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        AtomicReference<ProducerRecord<String, String>> recordRef = captureRecord();
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setTopic(null);

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        log.info("defaultTopic 兜底后的 record topic: {}", recordRef.get().topic());
        assertEquals("mock.default.topic", recordRef.get().topic(),
                "message 无 topic 且 resolver 返回 null 时应兜底到 defaultTopic");
    }

    @Test
    public void testTopicEmptyWithoutDefaultThrows() {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.setDefaultTopic(null);
        DefaultKafkaPublisher publisher = publisher(properties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setTopic(null);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(message));
        log.info("topic 为空错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_004, exception.getErrorCode(),
                "无 topic 且无 defaultTopic 应抛 topic 为空错误码");
    }

    @Test
    public void testKeyResolverCalledWhenNoApiOrMessageKey() throws Exception {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        KafkaPublishKeyResolver keyResolver = mock(KafkaPublishKeyResolver.class);
        when(keyResolver.resolveKey(any(KafkaPublishMessage.class))).thenReturn("resolved-key");
        DefaultKafkaPublisher publisher = publisher(properties,
                new DefaultKafkaPublishTopicResolver(), keyResolver);
        AtomicReference<ProducerRecord<String, String>> recordRef = captureRecord();
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setKey(null);

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        log.info("keyResolver 解析后的 record key: {}", recordRef.get().key());
        assertEquals("resolved-key", recordRef.get().key(),
                "API 与 message 均无 key 时应调用 keyResolver");
    }

    @Test
    public void testEnvelopeEnabledBooleanOverridesConfig() throws Exception {
        SimpleKafkaPublisherProperties onProperties = KafkaPublisherTestHelper.properties();
        onProperties.getEnvelope().setEnable(false);
        DefaultKafkaPublisher onPublisher = publisher(onProperties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        AtomicReference<ProducerRecord<String, String>> onRef = captureRecord();
        KafkaPublishMessage<String> onMessage = KafkaPublisherTestHelper.message();
        onMessage.setEnvelopeEnabled(true);
        onPublisher.publish(onMessage).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        SimpleKafkaPublisherProperties offProperties = KafkaPublisherTestHelper.properties();
        offProperties.getEnvelope().setEnable(true);
        DefaultKafkaPublisher offPublisher = publisher(offProperties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        AtomicReference<ProducerRecord<String, String>> offRef = captureRecord();
        KafkaPublishMessage<String> offMessage = KafkaPublisherTestHelper.message();
        offMessage.setEnvelopeEnabled(false);
        offPublisher.publish(offMessage).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        log.info("配置关+单条 true 覆盖 value: {}", onRef.get().value());
        log.info("配置开+单条 false 覆盖 value: {}", offRef.get().value());
        assertTrue(onRef.get().value().contains("\"messageId\""),
                "配置关闭时单条 envelopeEnabled=true 应覆盖为按 envelope 序列化");
        assertEquals(KafkaPublisherTestHelper.PAYLOAD, offRef.get().value(),
                "配置开启时单条 envelopeEnabled=false 应覆盖为 String payload 原样发送");
    }

    @Test
    public void testPerMessageEnvelopeRequiresAppNameWhenGlobalEnvelopeDisabled() {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.setAppName(" ");
        properties.getEnvelope().setEnable(false);
        properties.getHeaders().setEnableDefaultHeaders(false);
        DefaultKafkaPublisher publisher = publisher(properties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setEnvelopeEnabled(true);

        KafkaPublishConfigurationException exception = assertThrows(
                KafkaPublishConfigurationException.class, () -> publisher.publish(message));

        log.info("单条开启 envelope 且 app-name 为空的错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_001, exception.getErrorCode(),
                "单条开启 envelope 时空 app-name 应使用配置错误码");
    }

    @Test
    public void testEnvelopeEnabledNullFollowsConfig() throws Exception {
        SimpleKafkaPublisherProperties offProperties = KafkaPublisherTestHelper.properties();
        offProperties.getEnvelope().setEnable(false);
        DefaultKafkaPublisher offPublisher = publisher(offProperties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        AtomicReference<ProducerRecord<String, String>> offRef = captureRecord();
        KafkaPublishMessage<String> nullMessage = KafkaPublisherTestHelper.message();
        nullMessage.setEnvelopeEnabled(null);
        offPublisher.publish(nullMessage).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        log.info("envelopeEnabled=null 跟随关闭配置 value: {}", offRef.get().value());
        assertEquals(KafkaPublisherTestHelper.PAYLOAD, offRef.get().value(),
                "envelopeEnabled=null 应跟随全局关闭配置");
    }

    @Test
    public void testNullPayloadRejectedWhenIncludeNullPayloadFalse() {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getEnvelope().setIncludeNullPayload(false);
        DefaultKafkaPublisher publisher = publisher(properties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        KafkaPublishMessage<String> message = KafkaPublishMessage.<String>builder()
                .topic(KafkaPublisherTestHelper.TOPIC)
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                .payload(null)
                .envelopeEnabled(false)
                .build();

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(message));
        log.info("null payload 拒绝错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_003, exception.getErrorCode(),
                "include-null-payload=false 时 null payload 应被拒绝");
        assertFalse(exception.getMessage().contains("null"),
                "错误消息不应暴露 payload 内容");
    }

    @Test
    public void testNullPayloadAllowedWhenIncludeNullPayloadTrue() throws Exception {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getEnvelope().setEnable(true);
        properties.getEnvelope().setIncludeNullPayload(true);
        DefaultKafkaPublisher publisher = publisher(properties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        AtomicReference<ProducerRecord<String, String>> recordRef = captureRecord();
        KafkaPublishMessage<String> message = KafkaPublishMessage.<String>builder()
                .topic(KafkaPublisherTestHelper.TOPIC)
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                .payload(null)
                .envelopeEnabled(true)
                .build();

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        log.info("include-null-payload=true 时 envelope value: {}", recordRef.get().value());
        assertTrue(recordRef.get().value().contains("\"payload\":null"),
                "include-null-payload=true 时 null payload 应进入 envelope");
    }

    @Test
    public void testNegativePartitionAndTimestampRejected() {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        DefaultKafkaPublisher publisher = publisher(properties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        KafkaPublishMessage<String> partitionMessage = KafkaPublisherTestHelper.message();
        partitionMessage.setPartition(-1);
        KafkaPublishMessage<String> timestampMessage = KafkaPublisherTestHelper.message();
        timestampMessage.setTimestamp(-1L);

        KafkaPublishException partitionException = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(partitionMessage));
        KafkaPublishException timestampException = assertThrows(KafkaPublishException.class,
                () -> publisher.publish(timestampMessage));

        log.info("负 partition 错误: {}", partitionException.getMessage());
        log.info("负 timestamp 错误: {}", timestampException.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_005, partitionException.getErrorCode(),
                "负 partition 应使用 record 参数非法错误码");
        assertEquals(ErrorCode.KAFKA_PUBLISHER_005, timestampException.getErrorCode(),
                "负 timestamp 应使用 record 参数非法错误码");
    }

    @Test
    public void testEmptyHeaderValueProducesZeroLengthBytes() throws Exception {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        DefaultKafkaPublisher publisher = publisher(properties,
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver());
        AtomicReference<ProducerRecord<String, String>> recordRef = captureRecord();
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setHeaders(Collections.singletonMap(KafkaPublisherTestHelper.CUSTOM_HEADER, ""));

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        log.info("空字符串 header value 字节数: {}",
                recordRef.get().headers().lastHeader(KafkaPublisherTestHelper.CUSTOM_HEADER).value().length);
        assertArrayEquals(new byte[0],
                recordRef.get().headers().lastHeader(KafkaPublisherTestHelper.CUSTOM_HEADER).value(),
                "空字符串 header value 应转为 0 长度 UTF-8 字节数组");
    }

    @Test
    public void testCustomTopicResolverWinsOverDefault() throws Exception {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        KafkaPublishTopicResolver topicResolver = mock(KafkaPublishTopicResolver.class);
        when(topicResolver.resolveTopic(any(KafkaPublishMessage.class))).thenReturn("resolved-topic");
        DefaultKafkaPublisher publisher = publisher(properties, topicResolver,
                new DefaultKafkaPublishKeyResolver());
        AtomicReference<ProducerRecord<String, String>> recordRef = captureRecord();
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setTopic(null);
        properties.setDefaultTopic("should-not-be-used");

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        log.info("自定义 topicResolver 解析结果: {}", recordRef.get().topic());
        assertEquals("resolved-topic", recordRef.get().topic(),
                "自定义 topic resolver 返回非空时应优先于 defaultTopic");
    }

    @Test
    public void testCustomTraceResolverResultNormalizedByPublisher() throws Exception {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getEnvelope().setEnable(true);
        AtomicReference<ProducerRecord<String, String>> recordRef = captureRecord();
        DefaultKafkaPublisher publisher = new DefaultKafkaPublisher(routeTemplate, properties,
                new JacksonKafkaPublishSerializer(),
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver(),
                new DefaultKafkaPublishRouteKeyResolver(),
                () -> KafkaPublisherTestHelper.MESSAGE_ID,
                () -> "  mock-custom-trace  ",
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Collections.emptyList(), Collections.emptyList());
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setEnvelopeEnabled(true);

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);

        com.fasterxml.jackson.databind.JsonNode envelope =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(recordRef.get().value());
        log.info("自定义 trace resolver 标准化 value: {}", recordRef.get().value());
        assertEquals("mock-custom-trace", envelope.get("traceId").asText(),
                "发布引擎应对自定义 resolver 结果再次 trim");
        assertArrayEquals("mock-custom-trace".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                recordRef.get().headers().lastHeader(properties.getHeaders().getTraceIdHeader()).value(),
                "Envelope 与默认 trace header 应使用同一标准化值");
    }

    @Test
    public void testBlankCustomTraceResolverDoesNotCreateTraceHeader() throws Exception {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        AtomicReference<ProducerRecord<String, String>> recordRef = captureRecord();
        DefaultKafkaPublisher publisher = new DefaultKafkaPublisher(routeTemplate, properties,
                new JacksonKafkaPublishSerializer(),
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver(),
                new DefaultKafkaPublishRouteKeyResolver(),
                () -> KafkaPublisherTestHelper.MESSAGE_ID,
                () -> "  ",
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Collections.emptyList(), Collections.emptyList());

        publisher.publish(KafkaPublisherTestHelper.message()).get(
                KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

        log.info("blank 自定义 trace resolver header: {}",
                recordRef.get().headers().lastHeader(properties.getHeaders().getTraceIdHeader()));
        assertNull(recordRef.get().headers().lastHeader(properties.getHeaders().getTraceIdHeader()),
                "blank traceId 标准化为 null 后不应生成默认 trace header");
    }

    private AtomicReference<ProducerRecord<String, String>> captureRecord() {
        AtomicReference<ProducerRecord<String, String>> recordRef = new AtomicReference<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            recordRef.set(record);
            return KafkaPublisherTestHelper.successFuture(record);
        });
        return recordRef;
    }

    private DefaultKafkaPublisher publisher(SimpleKafkaPublisherProperties properties,
                                            KafkaPublishTopicResolver topicResolver,
                                            KafkaPublishKeyResolver keyResolver) {
        return new DefaultKafkaPublisher(routeTemplate, properties,
                new JacksonKafkaPublishSerializer(),
                topicResolver, keyResolver, new DefaultKafkaPublishRouteKeyResolver(),
                () -> KafkaPublisherTestHelper.MESSAGE_ID,
                () -> KafkaPublisherTestHelper.TRACE_ID,
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Collections.emptyList(), Collections.emptyList());
    }
}
