package io.github.surezzzzzz.sdk.crm.server.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 快照 JSON 编解码器：模块私有静态 ObjectMapper（禁 FAIL_ON_UNKNOWN 之外不改动容器配置），
 * 负责商业条款快照与履约义务模板的 Map 形态序列化，不承载业务语义。
 *
 * @author surezzzzzz
 */
public final class SimpleCrmServerJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SimpleCrmServerJsonCodec() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串；失败抛 IllegalStateException
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
     * @return LinkedHashMap 形态；失败抛 IllegalStateException
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
