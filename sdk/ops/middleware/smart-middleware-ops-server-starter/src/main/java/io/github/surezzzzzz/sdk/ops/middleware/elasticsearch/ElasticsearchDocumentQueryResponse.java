package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

/**
 * Elasticsearch 当前查询的原始结构响应。
 *
 * @author surezzzzzz
 */
@Getter
public class ElasticsearchDocumentQueryResponse {

    private final JsonNode value;

    /**
     * 创建 Elasticsearch 原始结构响应。
     *
     * @param value Elasticsearch 响应 JSON
     */
    public ElasticsearchDocumentQueryResponse(JsonNode value) {
        this.value = value;
    }

    @JsonValue
    public JsonNode jsonValue() {
        return value;
    }
}
