package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 索引字段详情响应
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexFieldsResponse {

    /**
     * 索引标识符
     */
    private String index;

    /**
     * 索引名称
     */
    private String indexName;

    /**
     * 实际索引列表
     */
    private List<String> actualIndices;

    /**
     * 字段列表
     */
    private List<FieldInfoResponse> fields;

    /**
     * 从元数据创建
     */
    public static IndexFieldsResponse from(IndexMetadata metadata) {
        List<FieldInfoResponse> fields = metadata.getFields().stream()
                .map(FieldInfoResponse::from)
                .collect(Collectors.toList());

        // 获取标识符（有alias用alias，没有用name）
        String identifier = (metadata.getAlias() != null && !metadata.getAlias().isEmpty())
                ? metadata.getAlias()
                : metadata.getIndexName();

        return IndexFieldsResponse.builder()
                .index(identifier)
                .indexName(metadata.getIndexName())
                .actualIndices(metadata.getActualIndices())
                .fields(fields)
                .build();
    }
}
