package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.summary;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * Elasticsearch 集群摘要请求校验器。
 *
 * @author surezzzzzz
 */
public class ElasticsearchSummaryRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<ElasticsearchSummaryRequest> {

    /**
     * 创建校验器。
     */
    public ElasticsearchSummaryRequestValidator() {
        super(ElasticsearchSummaryRequest.class);
    }

    @Override
    public void validate(ElasticsearchSummaryRequest request) {
        requireDatasource(request.getDatasourceKey());
    }
}
