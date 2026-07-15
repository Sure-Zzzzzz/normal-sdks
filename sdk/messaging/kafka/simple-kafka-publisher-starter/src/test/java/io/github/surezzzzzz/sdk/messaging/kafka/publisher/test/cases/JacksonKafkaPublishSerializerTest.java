package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jackson Kafka 发布序列化器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class JacksonKafkaPublishSerializerTest {

    @Test
    public void testStringPayloadPassesThrough() {
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer(new ObjectMapper());
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
    public void testUsesInjectedObjectMapperConfiguration() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer(objectMapper);
        String result = serializer.serialize(KafkaPublishSerializeContext.builder()
                .topic(KafkaPublisherTestHelper.TOPIC)
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                .payload(new MockPayload("mock-value"))
                .envelopeEnabled(false)
                .build());

        log.info("ObjectMapper 配置序列化结果: {}", result);
        assertEquals("{\"mock_value\":\"mock-value\"}", result,
                "应精确使用注入 ObjectMapper 的命名策略和字段值");
    }

    @Test
    public void testEnvelopeSerialized() throws Exception {
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer(new ObjectMapper());
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

        com.fasterxml.jackson.databind.JsonNode json = new ObjectMapper().readTree(result);
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
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonKafkaPublishSerializer serializer = new JacksonKafkaPublishSerializer(objectMapper);
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
    @Setter
    static class SelfReference {
        private SelfReference self;
    }
}
