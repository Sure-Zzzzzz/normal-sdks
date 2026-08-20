package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.summary;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Elasticsearch 集群版本能力摘要请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ElasticsearchSummaryRequest implements MiddlewareOpsRequest {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.ELASTICSEARCH_SUMMARY;
    }

    @Override
    public String getResourceScope() {
        return "cluster-summary";
    }
}
