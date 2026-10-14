package io.github.surezzzzzz.sdk.crm.client.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * wire 编解码器：模块私有静态 ObjectMapper，不读 Spring 全局；金额/数量由调用方以字符串进出。
 *
 * @author surezzzzzz
 */
public final class CrmWireCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private CrmWireCodec() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 序列化为 JSON。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("json write failed", e);
        }
    }

    /**
     * 反序列化为 Map。
     *
     * @param json JSON 字符串
     * @return LinkedHashMap
     */
    public static Map<String, Object> readMap(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("json read failed", e);
        }
    }
}
