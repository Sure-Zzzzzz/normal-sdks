package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import lombok.Value;

import java.util.List;

/**
 * 指定索引的可见字段目录。
 */
@Value
public class FieldDirectoryResponse {
    /**
     * 已通过敏感字段保护的元数据和展示标签。
     */
    List<FieldMetadata> fields;
}
