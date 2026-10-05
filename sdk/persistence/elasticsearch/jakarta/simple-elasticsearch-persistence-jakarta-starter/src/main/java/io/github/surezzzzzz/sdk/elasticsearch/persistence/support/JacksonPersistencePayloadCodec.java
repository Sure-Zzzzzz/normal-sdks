package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;

/**
 * 每个 SDK 实例独占 mapper，并固定 Java 时间编码。
 */
public class JacksonPersistencePayloadCodec implements PersistencePayloadCodec {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 编码独立快照，失败不携带源数据。
     */
    @Override
    public String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw failure();
        }
    }

    /**
     * 严格解码协议对象，失败不携带原始正文。
     */
    @Override
    public java.util.Map<String, Object> decode(String json) {
        try {
            java.util.Map<String, Object> result = mapper.readValue(json,
                    new TypeReference<java.util.Map<String, Object>>() {
                    });
            if (result == null) throw failure();
            return result;
        } catch (Exception error) {
            throw failure();
        }
    }

    /**
     * 生成与调用方对象隔离的 JSON 快照。
     */
    @Override
    public Object snapshot(Object value) {
        try {
            return mapper.readValue(encode(value), Object.class);
        } catch (Exception error) {
            throw failure();
        }
    }

    private PersistenceExecutionException failure() {
        // Jackson 原始错误可能含源文档片段，不能传入公开异常原因链。
        return new PersistenceExecutionException(ErrorCode.ES_REQUEST_BUILD_FAILED,
                ErrorMessage.ES_REQUEST_BUILD_FAILED);
    }
}
