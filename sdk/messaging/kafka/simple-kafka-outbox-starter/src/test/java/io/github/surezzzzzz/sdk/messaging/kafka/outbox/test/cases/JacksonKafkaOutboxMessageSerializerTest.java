package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.JacksonKafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jackson Kafka Outbox 消息快照序列化器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class JacksonKafkaOutboxMessageSerializerTest {

    private final JacksonKafkaOutboxMessageSerializer serializer = new JacksonKafkaOutboxMessageSerializer();

    @Test
    public void testOwnObjectMapperIsPrivateStaticFinal() throws Exception {
        Field mapperField = JacksonKafkaOutboxMessageSerializer.class.getDeclaredField("OBJECT_MAPPER");
        int modifiers = mapperField.getModifiers();
        mapperField.setAccessible(true);
        Object firstMapper = mapperField.get(serializer);
        Object secondMapper = mapperField.get(new JacksonKafkaOutboxMessageSerializer());

        log.info("ObjectMapper 字段修饰符: {}, 实例类型: {}, 两个序列化器是否共享内部实例: {}",
                Modifier.toString(modifiers), firstMapper.getClass().getName(), firstMapper == secondMapper);
        assertTrue(Modifier.isPrivate(modifiers), "内部 ObjectMapper 字段必须为 private");
        assertTrue(Modifier.isStatic(modifiers), "内部 ObjectMapper 字段必须为 static");
        assertTrue(Modifier.isFinal(modifiers), "内部 ObjectMapper 字段必须为 final");
        assertEquals(ObjectMapper.class, firstMapper.getClass(), "内部 JSON 实现应为独立 ObjectMapper");
        assertSame(firstMapper, secondMapper, "所有序列化器实例应复用模块私有 ObjectMapper");
    }

    @Test
    public void testStringNullAndJsonPayloadSerialization() throws Exception {
        String stringResult = serializer.serializePayload("mock-text");
        String nullResult = serializer.serializePayload(null);
        String jsonResult = serializer.serializePayload(new MockPayload("mock-value"));
        JsonNode json = new ObjectMapper().readTree(jsonResult);

        log.info("payload 序列化输入类型: STRING、NULL、JSON，输出: {}、{}、{}",
                stringResult, nullResult, jsonResult);
        assertEquals("mock-text", stringResult, "String payload 应原样保存，不能增加 JSON 引号");
        assertNull(nullResult, "null payload 应保存为 null");
        assertEquals("mock-value", json.get("mockValue").asText(), "对象 payload 应保存为 JSON");
        assertEquals(1, json.size(), "对象 payload JSON 不应产生额外字段");
    }

    @Test
    public void testJavaTimeUsesIso8601() throws Exception {
        JavaTimePayload payload = new JavaTimePayload(LocalDate.of(2026, 7, 18),
                LocalDateTime.of(2026, 7, 18, 9, 10, 11), Instant.parse("2026-07-18T01:10:11Z"));
        String result = serializer.serializePayload(payload);
        JsonNode json = new ObjectMapper().readTree(result);

        log.info("JavaTime payload 输入: {}, 输出: {}", payload, result);
        assertEquals("2026-07-18", json.get("localDate").asText(), "LocalDate 应使用 ISO-8601 字符串");
        assertEquals("2026-07-18T09:10:11", json.get("localDateTime").asText(),
                "LocalDateTime 应使用 ISO-8601 字符串");
        assertEquals("2026-07-18T01:10:11Z", json.get("instant").asText(),
                "Instant 应使用 ISO-8601 UTC 字符串");
        assertFalse(json.get("localDate").isNumber(), "JavaTime 不应写为时间戳数字");
    }

    @Test
    public void testMapSerializationAndCompleteSnapshotRestoration() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-mock", "mock-header");
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("mockCount", 2);
        String headersJson = serializer.serializeStringMap(headers);
        String attributesJson = serializer.serializeObjectMap(attributes);
        OutboxRecordEntity record = baseRecord(OutboxPayloadKind.JSON, "{\"mockValue\":\"mock-json\"}");
        record.setHeadersJson(headersJson);
        record.setAttributesJson(attributesJson);

        KafkaPublishMessage<Object> message = serializer.deserialize(record);
        JsonNode payload = (JsonNode) message.getPayload();

        log.info("完整快照输入: {}, 重建消息: {}, payload: {}, headers: {}, attributes: {}", record,
                message, payload, message.getHeaders(), message.getAttributes());
        assertEquals("mock-topic", message.getTopic(), "应恢复 topic");
        assertEquals("mock-key", message.getKey(), "应恢复 record key");
        assertEquals("mock-route", message.getRouteKey(), "应恢复 route key");
        assertEquals("mock-source", message.getDatasourceKey(), "应恢复 datasource key");
        assertEquals(Integer.valueOf(2), message.getPartition(), "应恢复 partition");
        assertEquals(Long.valueOf(100L), message.getTimestamp(), "应恢复 timestamp");
        assertEquals("mock-message-id", message.getMessageId(), "应恢复 messageId");
        assertEquals("mock-message-type", message.getMessageType(), "应恢复 messageType");
        assertEquals("mock-json", payload.get("mockValue").asText(), "JSON payload 应恢复为 JsonNode");
        assertEquals(headers, message.getHeaders(), "应精确恢复字符串 headers");
        assertEquals(2, ((Number) message.getAttributes().get("mockCount")).intValue(),
                "应精确恢复对象 attributes");
        assertEquals(Boolean.TRUE, message.getEnvelopeEnabled(), "应恢复 envelopeEnabled");

        log.info("null Map 序列化输入输出: headers=null -> {}, attributes=null -> {}",
                serializer.serializeStringMap(null), serializer.serializeObjectMap(null));
        assertNull(serializer.serializeStringMap(null), "null headers 应序列化为 null");
        assertNull(serializer.serializeObjectMap(null), "null attributes 应序列化为 null");
    }

    @Test
    public void testStringJsonAndNullSnapshotProtocols() {
        KafkaPublishMessage<Object> stringMessage = serializer.deserialize(
                baseRecord(OutboxPayloadKind.STRING, "plain-text"));
        KafkaPublishMessage<Object> jsonMessage = serializer.deserialize(
                baseRecord(OutboxPayloadKind.JSON, "{\"mock\":true}"));
        KafkaPublishMessage<Object> nullMessage = serializer.deserialize(
                baseRecord(OutboxPayloadKind.NULL, null));

        log.info("三种 payload 协议重建结果: STRING={}, JSON={}, NULL={}", stringMessage.getPayload(),
                jsonMessage.getPayload(), nullMessage.getPayload());
        assertEquals("plain-text", stringMessage.getPayload(), "STRING 协议应原样恢复文本");
        assertTrue(jsonMessage.getPayload() instanceof JsonNode, "JSON 协议应恢复为 JsonNode");
        assertEquals(true, ((JsonNode) jsonMessage.getPayload()).get("mock").asBoolean(),
                "JSON 协议应保留布尔字段");
        assertNull(nullMessage.getPayload(), "NULL 协议应恢复 null payload");
    }

    @Test
    public void testUnknownOrInconsistentProtocolIsRejected() {
        assertUnsupported(null, "记录为空");
        OutboxRecordEntity nullVersion = baseRecord(OutboxPayloadKind.STRING, "mock");
        nullVersion.setSchemaVersion(null);
        assertUnsupported(nullVersion, "schemaVersion 为空");
        OutboxRecordEntity unknownVersion = baseRecord(OutboxPayloadKind.STRING, "mock");
        unknownVersion.setSchemaVersion(SimpleKafkaOutboxConstant.SCHEMA_VERSION + 1);
        assertUnsupported(unknownVersion, "schemaVersion 未知");
        OutboxRecordEntity unknownKind = baseRecord(OutboxPayloadKind.STRING, "mock");
        unknownKind.setPayloadKind("UNKNOWN");
        assertUnsupported(unknownKind, "payloadKind 未知");
        assertUnsupported(baseRecord(OutboxPayloadKind.STRING, null), "STRING payload 内容为空");
        assertUnsupported(baseRecord(OutboxPayloadKind.JSON, null), "JSON payload 内容为空");
        assertUnsupported(baseRecord(OutboxPayloadKind.NULL, "unexpected"), "NULL payload 包含内容");
    }

    @Test
    public void testMalformedJsonUsesSnapshotFailure() {
        OutboxRecordEntity malformedPayload = baseRecord(OutboxPayloadKind.JSON, "{invalid-json}");
        log.info("待重建格式错误 JSON 输入: {}", malformedPayload.getPayloadJson());
        KafkaOutboxException exception = assertThrows(KafkaOutboxException.class,
                () -> serializer.deserialize(malformedPayload), "格式错误的 JSON 快照应重建失败");

        log.info("格式错误 JSON 输出错误码: {}, cause: {}", exception.getErrorCode(), exception.getCause());
        assertEquals(ErrorCode.KAFKA_OUTBOX_005, exception.getErrorCode(), "格式错误 JSON 应使用快照处理失败错误码");
        assertTrue(exception.getCause() != null, "格式错误 JSON 应保留 Jackson cause");
    }

    @Test
    public void testMalformedHeadersAndAttributesUseSnapshotFailure() {
        OutboxRecordEntity malformedHeaders = baseRecord(OutboxPayloadKind.STRING, "mock");
        malformedHeaders.setHeadersJson("[\"unexpected-array\"]");
        OutboxRecordEntity malformedAttributes = baseRecord(OutboxPayloadKind.STRING, "mock");
        malformedAttributes.setAttributesJson("[\"unexpected-array\"]");

        assertSnapshotFailed(malformedHeaders, "headersJson 不是对象");
        assertSnapshotFailed(malformedAttributes, "attributesJson 不是对象");
    }

    @Test
    public void testCyclicPayloadUsesSnapshotFailure() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);

        KafkaOutboxException exception = assertThrows(KafkaOutboxException.class,
                () -> serializer.serializePayload(cyclic), "循环 payload 必须拒绝持久化");

        assertEquals(ErrorCode.KAFKA_OUTBOX_005, exception.getErrorCode(), "循环 payload 必须使用快照处理失败错误码");
        assertTrue(exception.getCause() != null, "循环 payload 必须保留 Jackson cause");
    }

    private void assertSnapshotFailed(OutboxRecordEntity record, String scenario) {
        KafkaOutboxException exception = assertThrows(KafkaOutboxException.class,
                () -> serializer.deserialize(record), scenario + "必须拒绝重建");
        assertEquals(ErrorCode.KAFKA_OUTBOX_005, exception.getErrorCode(), scenario + "必须使用快照处理失败错误码");
        assertTrue(exception.getCause() != null, scenario + "必须保留 Jackson cause");
    }

    private void assertUnsupported(OutboxRecordEntity record, String scenario) {
        log.info("待校验不支持协议场景: {}, 输入: {}", scenario, record);
        KafkaOutboxException exception = assertThrows(KafkaOutboxException.class,
                () -> serializer.deserialize(record), scenario + "应被拒绝");

        log.info("不支持协议场景输出: {}, 错误码: {}, 错误消息: {}", scenario,
                exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_009, exception.getErrorCode(), scenario + "应使用协议不支持错误码");
        assertNull(exception.getCause(), scenario + "属于协议校验错误，不应附带底层 cause");
    }

    private OutboxRecordEntity baseRecord(OutboxPayloadKind kind, String payloadJson) {
        return OutboxRecordEntity.builder()
                .topic("mock-topic")
                .recordKey("mock-key")
                .routeKey("mock-route")
                .datasourceKey("mock-source")
                .partition(2)
                .messageTimestamp(100L)
                .messageId("mock-message-id")
                .messageType("mock-message-type")
                .payloadKind(kind.getCode())
                .payloadJson(payloadJson)
                .headersJson(serializer.serializeStringMap(Collections.singletonMap("x-base", "mock")))
                .attributesJson(serializer.serializeObjectMap(Collections.<String, Object>singletonMap("base", 1)))
                .envelopeEnabled(true)
                .schemaVersion(SimpleKafkaOutboxConstant.SCHEMA_VERSION)
                .build();
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
}
