package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.catalog;

import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.adapter.ElasticsearchOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Elasticsearch 索引目录执行器。
 *
 * @author surezzzzzz
 */
public class ElasticsearchIndexListExecutor
        extends AbstractMiddlewareOpsExecutor<ElasticsearchIndexListRequest, ElasticsearchIndexListResponse> {

    private final ElasticsearchOperationsViewAdapter adapter;

    /**
     * 创建索引目录执行器。
     *
     * @param adapter Elasticsearch Route 安全适配器
     */
    public ElasticsearchIndexListExecutor(ElasticsearchOperationsViewAdapter adapter) {
        super(ElasticsearchIndexListRequest.class);
        this.adapter = adapter;
    }

    @Override
    public ElasticsearchIndexListResponse execute(ElasticsearchIndexListRequest request) {
        return adapter.listIndices(request.getDatasourceKey());
    }
}
