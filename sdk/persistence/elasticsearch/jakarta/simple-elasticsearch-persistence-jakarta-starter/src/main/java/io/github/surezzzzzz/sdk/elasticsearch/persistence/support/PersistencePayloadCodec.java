package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import java.util.Map;

/**
 * 内部 JSON 编解码边界，不依赖宿主 ObjectMapper Bean。
 */
public interface PersistencePayloadCodec {
    /**
     * 序列化一个 SDK 请求对象。
     */
    String encode(Object value);

    /**
     * 解析 REST 响应对象，非法或非对象 JSON 必须失败。
     */
    Map<String, Object> decode(String json);

    /**
     * 建立文档快照，避免写入期间修改调用方对象。
     */
    Object snapshot(Object value);
}
