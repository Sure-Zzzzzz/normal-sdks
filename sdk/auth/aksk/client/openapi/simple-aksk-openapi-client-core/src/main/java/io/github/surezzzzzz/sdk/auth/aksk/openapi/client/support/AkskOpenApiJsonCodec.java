package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception.AkskOpenApiProtocolException;

/**
 * 管理 OpenAPI wire JSON 序列化封装（jackson 仅内部使用，公开接口零 jackson 类型暴露）。
 *
 * <p>未知字段容忍（server 契约新增字段不炸旧客户端）；反序列化失败抛
 * {@link AkskOpenApiProtocolException}，消息不含载荷原文。</p>
 *
 * @author surezzzzzz
 */
public final class AkskOpenApiJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

    private AkskOpenApiJsonCodec() {
    }

    /**
     * 序列化请求对象。
     *
     * @param value 请求对象
     * @return JSON 串
     * @throws AkskOpenApiProtocolException 序列化失败
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new AkskOpenApiProtocolException("请求序列化失败：" + value.getClass().getSimpleName(), null, null, null, e);
        }
    }

    /**
     * 反序列化响应。
     *
     * @param body 响应体
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化对象
     * @throws AkskOpenApiProtocolException 反序列化失败
     */
    public static <T> T read(String body, Class<T> type) {
        try {
            return MAPPER.readValue(body, type);
        } catch (Exception e) {
            throw new AkskOpenApiProtocolException("响应反序列化失败：" + type.getSimpleName(), null, null, null, e);
        }
    }

    /**
     * 提供 mapper 访问（各传输形态 starter 内部使用；不构成公开契约）。
     *
     * @return 共享 mapper 实例
     */
    static ObjectMapper mapper() {
        return MAPPER;
    }
}
