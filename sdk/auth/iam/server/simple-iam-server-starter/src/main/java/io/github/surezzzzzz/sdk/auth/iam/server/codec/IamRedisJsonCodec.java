package io.github.surezzzzzz.sdk.auth.iam.server.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;

/**
 * IAM Redis 运行态 JSON 编解码器。
 *
 * @author surezzzzzz
 */
public final class IamRedisJsonCodec {

    private final ObjectMapper objectMapper;

    public IamRedisJsonCodec() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 对象序列化为 JSON 字节（Redis value）
     */
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new SimpleIamServerException(
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, "JSON序列化失败"), ex);
        }
    }

    /**
     * JSON 字节反序列化为对象
     */
    public <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new SimpleIamServerException(
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, "JSON反序列化失败"), ex);
        }
    }
}
