package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import java.util.Map;

/**
 * SDK JSON 边界；实现不得借用应用 ObjectMapper。
 */
public interface SearchPayloadCodec {
    /**
     * 编码独立快照，失败不携带源数据。
     */
    String encode(Object value);

    /**
     * 严格解码协议对象，失败不携带原始正文。
     */
    Map<String, Object> decode(String json);

    /**
     * 冻结请求对象，避免执行期间读取调用方修改。
     */
    <T> T copy(T value, Class<T> type);

    /**
     * 将内部协议对象还原为指定载荷类型。
     */
    <T> T copyMap(Map<String, Object> value, Class<T> type);
}
