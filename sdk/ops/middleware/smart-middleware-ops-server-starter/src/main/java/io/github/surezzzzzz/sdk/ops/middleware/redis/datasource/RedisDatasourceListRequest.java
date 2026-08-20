package io.github.surezzzzzz.sdk.ops.middleware.redis.datasource;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;

/**
 * Redis 数据源清单请求。
 *
 * @author surezzzzzz
 */
public class RedisDatasourceListRequest implements MiddlewareOpsRequest {

    private final boolean auditRequired;

    public RedisDatasourceListRequest() {
        this(true);
    }

    private RedisDatasourceListRequest(boolean auditRequired) {
        this.auditRequired = auditRequired;
    }

    /**
     * 创建概览自动加载请求。
     *
     * @return 不写审计的概览请求
     */
    public static RedisDatasourceListRequest forOverview() {
        return new RedisDatasourceListRequest(false);
    }

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.REDIS_DATASOURCE_LIST;
    }

    @Override
    public String getDatasourceKey() {
        return "all";
    }

    @Override
    public String getResourceScope() {
        return "datasource-list";
    }

    @Override
    public boolean isAuditRequired() {
        return auditRequired;
    }
}
