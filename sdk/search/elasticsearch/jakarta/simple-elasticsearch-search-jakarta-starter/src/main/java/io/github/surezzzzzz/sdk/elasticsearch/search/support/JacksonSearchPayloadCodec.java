package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * 内部独占、确定性 JSON 编码；异常不携带源数据。
 */
public class JacksonSearchPayloadCodec implements SearchPayloadCodec {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /**
     * 编码独立快照，失败不携带源数据。
     */
    public String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw SearchValidationHelper.invalid();
        }
    }

    /**
     * 严格解码协议对象，失败不携带原始正文。
     */
    public Map<String, Object> decode(String json) {
        try {
            Map<String, Object> value = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            if (value == null) throw SearchValidationHelper.protocol();
            return value;
        } catch (Exception error) {
            throw SearchValidationHelper.protocol();
        }
    }

    /**
     * 冻结请求对象，避免执行期间读取调用方修改。
     */
    public <T> T copy(T value, Class<T> type) {
        if (value == null) throw SearchValidationHelper.invalid();
        try {
            return mapper.readValue(encode(value), type);
        } catch (Exception error) {
            throw SearchValidationHelper.invalid();
        }
    }

    /**
     * 将内部协议对象还原为指定载荷类型。
     */
    public <T> T copyMap(Map<String, Object> value, Class<T> type) {
        try {
            return mapper.readValue(encode(value), type);
        } catch (Exception error) {
            throw SearchValidationHelper.invalid();
        }
    }
}
