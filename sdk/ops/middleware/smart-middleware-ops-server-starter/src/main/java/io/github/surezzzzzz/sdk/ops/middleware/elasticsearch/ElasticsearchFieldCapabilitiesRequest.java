package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Elasticsearch 字段能力目录请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ElasticsearchFieldCapabilitiesRequest implements MiddlewareOpsRequest {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 精确索引名称。
     */
    private final String index;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.ELASTICSEARCH_FIELD_CAPABILITIES;
    }

    @Override
    public String getResourceScope() {
        return "field-capabilities:" + index;
    }
}
