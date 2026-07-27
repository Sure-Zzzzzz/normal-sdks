package io.github.surezzzzzz.sdk.kms.server.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * KMS HTTP 模块私有 JSON 编解码器。
 *
 * @author surezzzzzz
 */
public final class KmsHttpJson {

    /**
     * 仅供 KMS HTTP 模块使用的独立 JSON 编解码器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private KmsHttpJson() {
        throw new UnsupportedOperationException(SmartKmsServerConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 解析必须为 JSON 对象的请求体。
     *
     * @param body          原始请求体
     * @param allowedFields 允许字段名
     * @return 已验证的 JSON 对象
     */
    public static ObjectNode parseObject(String body, String... allowedFields) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(body);
            if (!(node instanceof ObjectNode)) {
                throw new KmsValidationException();
            }
            ObjectNode objectNode = (ObjectNode) node;
            Set<String> allowed = new HashSet<String>();
            for (String allowedField : allowedFields) {
                allowed.add(allowedField);
            }
            Iterator<String> fields = objectNode.fieldNames();
            while (fields.hasNext()) {
                if (!allowed.contains(fields.next())) {
                    throw new KmsValidationException();
                }
            }
            return objectNode;
        } catch (JsonProcessingException exception) {
            throw new KmsValidationException();
        }
    }

    /**
     * 解析只要求为 JSON 对象的内部响应快照。
     *
     * @param body JSON 文本
     * @return JSON 对象
     */
    public static ObjectNode parseSnapshotObject(String body) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(body);
            if (!(node instanceof ObjectNode)) {
                throw new KmsValidationException();
            }
            return (ObjectNode) node;
        } catch (JsonProcessingException exception) {
            throw new KmsValidationException();
        }
    }

    /**
     * 将时间统一格式化为 UTC RFC 3339 毫秒字符串。
     *
     * @param instant UTC 时间点
     * @return 固定三位毫秒的 UTC 字符串
     */
    public static String utcMillis(Instant instant) {
        if (instant == null) {
            return null;
        }
        return UTC_MILLIS.format(instant);
    }

    /**
     * 将已校验 JSON 对象按字段名排序，生成稳定请求摘要文本。
     *
     * @param object 已校验 JSON 对象
     * @return 字段顺序无关的 JSON 文本
     */
    public static String canonical(ObjectNode object) {
        if (object == null) {
            throw new KmsValidationException();
        }
        return write(canonicalNode(object));
    }

    /**
     * 序列化无敏感响应对象。
     *
     * @param value 无敏感响应对象
     * @return JSON UTF-8 文本
     */
    public static String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new KmsValidationException();
        }
    }

    /**
     * 将 JSON 节点递归转换为排序后的普通对象。
     */
    private static Object canonicalNode(JsonNode node) {
        if (node instanceof ObjectNode) {
            Map<String, Object> result = new TreeMap<String, Object>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.put(field.getKey(), canonicalNode(field.getValue()));
            }
            return result;
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<Object>();
            for (JsonNode item : node) {
                result.add(canonicalNode(item));
            }
            return result;
        }
        if (node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return Boolean.valueOf(node.booleanValue());
        }
        if (node.isIntegralNumber()) {
            return node.numberValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        throw new KmsValidationException();
    }
}
