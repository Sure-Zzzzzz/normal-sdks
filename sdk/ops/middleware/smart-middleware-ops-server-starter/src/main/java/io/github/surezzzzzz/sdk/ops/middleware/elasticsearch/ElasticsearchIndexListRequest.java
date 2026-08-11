package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Elasticsearch 索引目录请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ElasticsearchIndexListRequest implements MiddlewareOpsRequest {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.ELASTICSEARCH_INDEX_LIST;
    }

    @Override
    public String getResourceScope() {
        return "index-list";
    }
}
