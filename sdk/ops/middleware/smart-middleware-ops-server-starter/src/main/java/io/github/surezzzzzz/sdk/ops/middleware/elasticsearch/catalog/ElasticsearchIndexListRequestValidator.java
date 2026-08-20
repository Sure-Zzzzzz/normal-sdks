package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.catalog;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * Elasticsearch 索引目录请求校验器。
 *
 * @author surezzzzzz
 */
public class ElasticsearchIndexListRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<ElasticsearchIndexListRequest> {

    /**
     * 创建校验器。
     */
    public ElasticsearchIndexListRequestValidator() {
        super(ElasticsearchIndexListRequest.class);
    }

    @Override
    public void validate(ElasticsearchIndexListRequest request) {
        requireDatasource(request.getDatasourceKey());
    }
}
