package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Elasticsearch 集群摘要执行器。
 *
 * @author surezzzzzz
 */
public class ElasticsearchSummaryExecutor
        extends AbstractMiddlewareOpsExecutor<ElasticsearchSummaryRequest, ElasticsearchSummaryResponse> {

    private final ElasticsearchOperationsViewAdapter adapter;

    /**
     * 创建执行器。
     *
     * @param adapter Elasticsearch Route 安全适配器
     */
    public ElasticsearchSummaryExecutor(ElasticsearchOperationsViewAdapter adapter) {
        super(ElasticsearchSummaryRequest.class);
        this.adapter = adapter;
    }

    @Override
    public ElasticsearchSummaryResponse execute(ElasticsearchSummaryRequest request) {
        return adapter.getSummary(request.getDatasourceKey());
    }
}
