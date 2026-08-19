package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Elasticsearch 字段能力目录执行器。
 *
 * @author surezzzzzz
 */
public class ElasticsearchFieldCapabilitiesExecutor extends AbstractMiddlewareOpsExecutor<ElasticsearchFieldCapabilitiesRequest,
        ElasticsearchFieldCapabilitiesResponse> {

    private final ElasticsearchOperationsViewAdapter adapter;

    /**
     * 创建字段能力目录执行器。
     *
     * @param adapter Elasticsearch 运维视图适配器
     */
    public ElasticsearchFieldCapabilitiesExecutor(ElasticsearchOperationsViewAdapter adapter) {
        super(ElasticsearchFieldCapabilitiesRequest.class);
        this.adapter = adapter;
    }

    @Override
    public ElasticsearchFieldCapabilitiesResponse execute(ElasticsearchFieldCapabilitiesRequest request) {
        return adapter.getFieldCapabilities(request);
    }
}
