package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.catalog;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Elasticsearch 索引目录响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ElasticsearchIndexListResponse {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 可供前端提示的索引名。
     */
    private final List<String> items;
    /**
     * 是否因固定上限截断。
     */
    private final boolean truncated;
}
