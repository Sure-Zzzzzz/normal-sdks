package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Elasticsearch JSON DSL 文档查询请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ElasticsearchDocumentQueryRequest implements MiddlewareOpsRequest {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 调用方手工输入的索引。
     */
    private final String index;
    /**
     * 已解码的 JSON DSL。
     */
    private final String dsl;
    /**
     * 当前页码。
     */
    @Builder.Default
    private final int page = 1;
    /**
     * 单页结果数量。
     */
    private final int size;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.ELASTICSEARCH_DOCUMENT_QUERY;
    }

    @Override
    public String getResourceScope() {
        return "document-query:" + index + ":" + dsl;
    }
}
