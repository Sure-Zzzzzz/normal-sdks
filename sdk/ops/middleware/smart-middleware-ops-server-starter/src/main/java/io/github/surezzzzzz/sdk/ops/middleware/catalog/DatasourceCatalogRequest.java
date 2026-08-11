package io.github.surezzzzzz.sdk.ops.middleware.catalog;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import lombok.Getter;

/**
 * 已初始化数据源目录请求。
 *
 * @author surezzzzzz
 */
@Getter
public class DatasourceCatalogRequest implements MiddlewareOpsRequest {

    private final MiddlewareType middlewareType;

    /**
     * 创建固定工作区的数据源目录请求。
     *
     * @param middlewareType 工作区类型
     */
    public DatasourceCatalogRequest(MiddlewareType middlewareType) {
        this.middlewareType = middlewareType;
    }

    @Override
    public MiddlewareOpsCapability getCapability() {
        if (middlewareType == MiddlewareType.ELASTICSEARCH) {
            return MiddlewareOpsCapability.ELASTICSEARCH_DATASOURCE_CATALOG;
        }
        if (middlewareType == MiddlewareType.REDIS) {
            return MiddlewareOpsCapability.REDIS_DATASOURCE_CATALOG;
        }
        if (middlewareType == MiddlewareType.KAFKA) {
            return MiddlewareOpsCapability.KAFKA_DATASOURCE_CATALOG;
        }
        if (middlewareType == MiddlewareType.MYSQL) {
            return MiddlewareOpsCapability.MYSQL_DATASOURCE_CATALOG;
        }
        return null;
    }

    @Override
    public String getDatasourceKey() {
        return null;
    }

    @Override
    public String getResourceScope() {
        return middlewareType == null ? null : middlewareType.getCode() + "-catalog";
    }
}
