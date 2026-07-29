package io.github.surezzzzzz.sdk.kms.client.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsProtocolException;

/**
 * KMS Client 独立 JSON 编解码器。
 *
 * <p>自行维护 ObjectMapper，不注入或复用宿主应用的 Bean，避免宿主序列化策略改变 KMS HTTP 契约。</p>
 *
 * @author surezzzzzz
 */
public class KmsJsonCodec {

    private final ObjectMapper objectMapper;

    /**
     * 创建独立 JSON 编解码器。
     */
    public KmsJsonCodec() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 序列化请求 JSON。
     *
     * @param value 待序列化对象
     * @return JSON UTF-8 字节
     * @throws KmsProtocolException 序列化失败时抛出
     */
    public byte[] write(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new KmsProtocolException(SimpleKmsClientConstant.MESSAGE_PROTOCOL_ERROR);
        }
    }

    /**
     * 解析响应 JSON。
     *
     * @param value JSON UTF-8 字节
     * @return 解析后的 JSON 节点
     * @throws KmsProtocolException 响应不是合法 JSON 时抛出
     */
    public JsonNode read(byte[] value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new KmsProtocolException(SimpleKmsClientConstant.MESSAGE_PROTOCOL_ERROR);
        }
    }
}
