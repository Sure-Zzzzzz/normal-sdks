package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import lombok.Value;

import java.util.List;

/**
 * 字段仅包含可用于条件的元数据，不返回文档值或禁止字段。
 */
@Value
public class ExpressionHintsResponse {
    List<FieldMetadata> fields;
    List<String> operators;
    List<String> timeRanges;
}
