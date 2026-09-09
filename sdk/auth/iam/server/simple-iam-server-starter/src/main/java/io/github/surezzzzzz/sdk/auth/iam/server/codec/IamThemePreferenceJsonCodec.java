package io.github.surezzzzzz.sdk.auth.iam.server.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IAM 用户主题偏好 JSON 编解码器。
 *
 * @author surezzzzzz
 */
public final class IamThemePreferenceJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private static final JavaType STRING_MAP_TYPE = OBJECT_MAPPER.getTypeFactory()
            .constructMapType(LinkedHashMap.class, String.class, String.class);

    private IamThemePreferenceJsonCodec() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 主题偏好序列化为 JSON 字符串（Redis value）
     */
    public static String write(Map<String, String> tokens) {
        try {
            return OBJECT_MAPPER.writeValueAsString(tokens);
        } catch (JsonProcessingException exception) {
            throw new SimpleIamServerException("主题偏好数据无效", exception);
        }
    }

    /**
     * JSON 字符串反序列化为主题偏好
     */
    public static Map<String, String> read(String value) {
        if (value == null) {
            throw invalid();
        }
        try {
            Map<String, String> tokens = OBJECT_MAPPER.readValue(value, STRING_MAP_TYPE);
            if (tokens == null) {
                throw invalid();
            }
            return tokens;
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    private static SimpleIamServerException invalid() {
        return new SimpleIamServerException("主题偏好数据无效");
    }
}
