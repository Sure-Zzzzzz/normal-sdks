package io.github.surezzzzzz.sdk.retry.redis.smart.serializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryOperationException;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * Jackson 重试上下文序列化器
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class JacksonRetryContextSerializer implements RetryContextSerializer {

    /**
     * Jackson 对象映射器
     */
    private final ObjectMapper objectMapper;

    /**
     * 序列化上下文为 JSON 字符串。
     *
     * @param context 上下文 Map，null 或空 Map 返回 null
     * @return JSON 字符串
     */
    @Override
    public String serialize(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.JSON_SERIALIZE_FAILED, ErrorMessage.JSON_SERIALIZE_FAILED, e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为上下文 Map。
     *
     * @param contextJson JSON 字符串，null 或空字符串返回空 Map
     * @return 上下文 Map
     */
    @Override
    public Map<String, Object> deserialize(String contextJson) {
        if (contextJson == null || contextJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(contextJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.JSON_DESERIALIZE_FAILED, ErrorMessage.JSON_DESERIALIZE_FAILED, e);
        }
    }
}
