package io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jackson Kafka Outbox 消息快照序列化器
 *
 * @author surezzzzzz
 */
@SimpleKafkaOutboxComponent
@ConditionalOnMissingBean(KafkaOutboxMessageSerializer.class)
public class JacksonKafkaOutboxMessageSerializer implements KafkaOutboxMessageSerializer {

    /**
     * 模块私有 ObjectMapper，内置 JavaTimeModule 且不写时间戳，禁止注入或复用 Spring Bean
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    /**
     * headers Map 的 JSON 反序列化类型
     */
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP_TYPE =
            new TypeReference<LinkedHashMap<String, String>>() {
            };
    /**
     * attributes Map 的 JSON 反序列化类型
     */
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    /**
     * 序列化 payload
     *
     * @param payload payload
     * @return 快照文本
     */
    @Override
    public String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String) {
            return (String) payload;
        }
        return writeJson(payload);
    }

    /**
     * 序列化字符串 Map
     *
     * @param value Map
     * @return JSON 文本
     */
    @Override
    public String serializeStringMap(Map<String, String> value) {
        return value == null ? null : writeJson(value);
    }

    /**
     * 序列化对象 Map
     *
     * @param value Map
     * @return JSON 文本
     */
    @Override
    public String serializeObjectMap(Map<String, Object> value) {
        return value == null ? null : writeJson(value);
    }

    /**
     * 从记录重建发布消息
     *
     * @param record Outbox 记录
     * @return 发布消息
     */
    @Override
    public KafkaPublishMessage<Object> deserialize(OutboxRecordEntity record) {
        if (record == null || record.getSchemaVersion() == null
                || record.getSchemaVersion() != SimpleKafkaOutboxConstant.SCHEMA_VERSION) {
            throw unsupportedSnapshot();
        }
        OutboxPayloadKind kind = OutboxPayloadKind.fromCode(record.getPayloadKind());
        if (kind == null) {
            throw unsupportedSnapshot();
        }
        try {
            Object payload;
            if (kind == OutboxPayloadKind.STRING) {
                if (record.getPayloadJson() == null) {
                    throw unsupportedSnapshot();
                }
                payload = record.getPayloadJson();
            } else if (kind == OutboxPayloadKind.JSON) {
                if (record.getPayloadJson() == null) {
                    throw unsupportedSnapshot();
                }
                payload = OBJECT_MAPPER.readTree(record.getPayloadJson());
            } else {
                if (record.getPayloadJson() != null) {
                    throw unsupportedSnapshot();
                }
                payload = null;
            }
            Map<String, String> headers = record.getHeadersJson() == null ? null
                    : OBJECT_MAPPER.readValue(record.getHeadersJson(), STRING_MAP_TYPE);
            Map<String, Object> attributes = record.getAttributesJson() == null ? null
                    : OBJECT_MAPPER.readValue(record.getAttributesJson(), OBJECT_MAP_TYPE);
            return KafkaPublishMessage.<Object>builder()
                    .topic(record.getTopic())
                    .key(record.getRecordKey())
                    .routeKey(record.getRouteKey())
                    .datasourceKey(record.getDatasourceKey())
                    .partition(record.getPartition())
                    .timestamp(record.getMessageTimestamp())
                    .messageId(record.getMessageId())
                    .messageType(record.getMessageType())
                    .payload(payload)
                    .headers(headers)
                    .attributes(attributes)
                    .envelopeEnabled(record.getEnvelopeEnabled())
                    .build();
        } catch (JsonProcessingException | RuntimeException e) {
            if (e instanceof KafkaOutboxException) {
                throw (KafkaOutboxException) e;
            }
            throw snapshotFailed(e);
        }
    }

    /**
     * 序列化对象为 JSON 文本，StackOverflow 单独处理为快照失败。
     */
    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException | RuntimeException e) {
            throw snapshotFailed(e);
        } catch (StackOverflowError e) {
            throw snapshotFailed(e);
        }
    }

    /**
     * 构造快照处理失败异常，错误码 KAFKA_OUTBOX_005。
     */
    private KafkaOutboxException snapshotFailed(Throwable cause) {
        return new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_005, ErrorMessage.KAFKA_OUTBOX_005, cause);
    }

    /**
     * 构造不受支持的快照协议或 payload 类型异常，错误码 KAFKA_OUTBOX_009。
     */
    private KafkaOutboxException unsupportedSnapshot() {
        return new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_009, ErrorMessage.KAFKA_OUTBOX_009);
    }
}
