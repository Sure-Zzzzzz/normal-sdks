package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ValidationException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.claim.DataGrantDocumentClaimMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AKSK应用授权持久化JSON编解码器。
 *
 * @author surezzzzzz
 */
public final class AkskApplicationAuthorizationJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private static final JavaType STRING_LIST_TYPE = OBJECT_MAPPER.getTypeFactory()
            .constructCollectionType(List.class, String.class);
    private static final JavaType MAP_TYPE = OBJECT_MAPPER.getTypeFactory()
            .constructMapType(Map.class, String.class, Object.class);

    private AkskApplicationAuthorizationJsonCodec() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    public static List<String> readStringList(String value) {
        if (value == null) {
            throw invalid();
        }
        try {
            List<String> result = OBJECT_MAPPER.readValue(value, STRING_LIST_TYPE);
            if (result == null) {
                throw invalid();
            }
            return Collections.unmodifiableList(new ArrayList<String>(result));
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    /**
     * 将字符串集合编码为持久化 JSON。
     *
     * @param values 字符串集合
     * @return 规范化持久化 JSON
     */
    public static String writeStringList(List<String> values) {
        if (values == null) {
            throw invalid();
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    /**
     * 将已校验的数据授权文档编码为持久化 JSON。
     *
     * @param document 数据授权文档
     * @return 规范化持久化 JSON
     */
    public static String writeDataGrantDocument(DataGrantDocument document) {
        if (document == null) {
            throw invalid();
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(DataGrantDocumentClaimMapper.toClaim(document));
        } catch (RuntimeException exception) {
            throw invalid();
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    /**
     * 从持久化 JSON 读取数据授权文档。
     *
     * @param value 持久化 JSON；null 表示未授予 DATA 权限
     * @return 数据授权文档；未授予时返回 null
     */
    public static DataGrantDocument readDataGrantDocument(String value) {
        if (value == null) {
            return null;
        }
        try {
            Map<String, Object> claim = OBJECT_MAPPER.readValue(value, MAP_TYPE);
            return DataGrantDocumentClaimMapper.fromClaim(claim);
        } catch (RuntimeException exception) {
            throw invalid();
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    private static ValidationException invalid() {
        return new ValidationException("AKSK应用授权数据无效");
    }
}
