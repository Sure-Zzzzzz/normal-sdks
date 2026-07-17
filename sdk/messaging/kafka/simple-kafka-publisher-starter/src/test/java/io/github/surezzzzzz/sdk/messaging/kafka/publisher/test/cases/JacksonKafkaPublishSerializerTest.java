package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishEnvelope;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishSerializeContext;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Jackson Kafka 发布序列化器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class JacksonKafkaPublishSerializerTest {

    @Test
    public void testStringPayloadPassesThrough() {
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer();
        String result = serializer.serialize(KafkaPublishSerializeContext.builder()
                .topic(KafkaPublisherTestHelper.TOPIC)
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                .payload(KafkaPublisherTestHelper.PAYLOAD)
                .envelopeEnabled(false)
                .build());

        log.info("String payload 序列化结果: {}", result);
        assertEquals(KafkaPublisherTestHelper.PAYLOAD, result, "String payload 不应额外 JSON quote");
    }

    @Test
    public void testUsesPrivateDefaultObjectMapper() {
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer();
        String result = serializer.serialize(KafkaPublishSerializeContext.builder()
                .topic(KafkaPublisherTestHelper.TOPIC)
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                .payload(new MockPayload("mock-value"))
                .envelopeEnabled(false)
                .build());

        log.info("私有 ObjectMapper 序列化结果: {}", result);
        assertEquals("{\"mockValue\":\"mock-value\"}", result,
                "默认序列化器应使用自身 ObjectMapper 的默认命名策略");
    }

    @Test
    public void testJavaTimePayloadUsesIso8601() throws Exception {
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer();
        JavaTimePayload payload = new JavaTimePayload(
                LocalDate.of(2026, 7, 17),
                LocalDateTime.of(2026, 7, 17, 14, 30, 45),
                Instant.parse("2026-07-17T06:30:45Z"));

        String result = serializer.serialize(KafkaPublishSerializeContext.builder()
                .topic(KafkaPublisherTestHelper.TOPIC)
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                .payload(payload)
                .envelopeEnabled(false)
                .build());
        JsonNode json = new ObjectMapper().readTree(result);

        log.info("Java 8 时间类型序列化结果: {}", result);
        assertEquals("2026-07-17", json.get("localDate").asText(),
                "LocalDate 应使用 ISO-8601 字符串");
        assertEquals("2026-07-17T14:30:45", json.get("localDateTime").asText(),
                "LocalDateTime 应使用 ISO-8601 字符串");
        assertEquals("2026-07-17T06:30:45Z", json.get("instant").asText(),
                "Instant 应使用 ISO-8601 UTC 字符串");
    }

    @Test
    public void testEnvelopeSerialized() throws Exception {
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer();
        KafkaPublishEnvelope<String> envelope = KafkaPublishEnvelope.<String>builder()
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                .payload(KafkaPublisherTestHelper.PAYLOAD)
                .build();
        String result = serializer.serialize(KafkaPublishSerializeContext.builder()
                .topic(KafkaPublisherTestHelper.TOPIC)
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                .payload(KafkaPublisherTestHelper.PAYLOAD)
                .envelope(envelope)
                .envelopeEnabled(true)
                .build());

        JsonNode json = new ObjectMapper().readTree(result);
        log.info("Envelope 序列化结果: {}", result);
        assertEquals(KafkaPublisherTestHelper.MESSAGE_ID, json.get("messageId").asText(),
                "Envelope messageId 应精确一致");
        assertEquals(KafkaPublisherTestHelper.MESSAGE_TYPE, json.get("messageType").asText(),
                "Envelope messageType 应精确一致");
        assertEquals(KafkaPublisherTestHelper.PAYLOAD, json.get("payload").asText(),
                "Envelope payload 应精确一致");
    }

    @Test
    public void testSerializeFailureDoesNotExposePayload() {
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer();
        SelfReference payload = new SelfReference();
        payload.setSelf(payload);

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> serializer.serialize(KafkaPublishSerializeContext.builder()
                        .topic(KafkaPublisherTestHelper.TOPIC)
                        .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                        .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                        .payload(payload)
                        .envelopeEnabled(false)
                        .build()));

        log.info("序列化失败错误消息: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_006, exception.getErrorCode(), "错误码应为序列化失败");
        assertFalse(exception.getMessage().contains(payload.toString()), "错误消息不应包含 payload 内容");
    }

    @Getter
    @AllArgsConstructor
    static class MockPayload {
        private final String mockValue;
    }

    @Getter
    @AllArgsConstructor
    static class JavaTimePayload {
        private final LocalDate localDate;
        private final LocalDateTime localDateTime;
        private final Instant instant;
    }

    @Getter
    @Setter
    static class SelfReference {
        private SelfReference self;
    }
}
