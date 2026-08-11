package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Elasticsearch JSON DSL 文档查询执行器。
 *
 * @author surezzzzzz
 */
public class ElasticsearchDocumentQueryExecutor
        extends AbstractMiddlewareOpsExecutor<ElasticsearchDocumentQueryRequest, ElasticsearchDocumentQueryResponse> {

    private final ElasticsearchOperationsViewAdapter adapter;

    /**
     * 创建 Elasticsearch 文档查询执行器。
     *
     * @param adapter Elasticsearch Route 安全适配器
     */
    public ElasticsearchDocumentQueryExecutor(ElasticsearchOperationsViewAdapter adapter) {
        super(ElasticsearchDocumentQueryRequest.class);
        this.adapter = adapter;
    }

    @Override
    public ElasticsearchDocumentQueryResponse execute(ElasticsearchDocumentQueryRequest request) {
        return adapter.queryDocuments(request);
    }
}
