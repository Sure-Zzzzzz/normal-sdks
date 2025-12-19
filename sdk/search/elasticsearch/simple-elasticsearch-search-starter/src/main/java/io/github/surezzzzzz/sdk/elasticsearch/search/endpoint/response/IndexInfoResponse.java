package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 索引信息响应
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexInfoResponse {

    /**
     * 索引别名
     */
    private String alias;

    /**
     * 索引名称（支持通配符）
     */
    private String name;

    /**
     * 是否按日期分割
     */
    private boolean dateSplit;

    /**
     * 字段数量
     */
    private int fieldCount;

    /**
     * 从配置创建
     */
    public static IndexInfoResponse from(SimpleElasticsearchSearchProperties.IndexConfig config, MappingManager mappingManager) {
        IndexInfoResponseBuilder builder = IndexInfoResponse.builder()
                .alias(config.getAlias())
                .name(config.getName())
                .dateSplit(config.isDateSplit());

        // 获取字段数量（使用标识符查询：有alias用alias，没有用name）
        try {
            String identifier = (config.getAlias() != null && !config.getAlias().isEmpty())
                    ? config.getAlias()
                    : config.getName();
            IndexMetadata metadata = mappingManager.getMetadata(identifier);
            builder.fieldCount(metadata.getFields().size());
        } catch (Exception e) {
            builder.fieldCount(0);
        }

        return builder.build();
    }
}
