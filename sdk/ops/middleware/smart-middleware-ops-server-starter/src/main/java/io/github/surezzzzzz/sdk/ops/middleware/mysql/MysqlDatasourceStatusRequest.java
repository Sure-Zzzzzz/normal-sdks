package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * MySQL 数据源受控状态探测请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlDatasourceStatusRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;

    @Builder.Default
    private final boolean auditRequired = true;

    /**
     * 创建概览自动加载请求。
     *
     * @param datasourceKey 数据源标识
     * @return 不写审计的概览请求
     */
    public static MysqlDatasourceStatusRequest forOverview(String datasourceKey) {
        return MysqlDatasourceStatusRequest.builder().datasourceKey(datasourceKey).auditRequired(false).build();
    }

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.MYSQL_DATASOURCE_STATUS;
    }

    @Override
    public String getResourceScope() {
        return "datasource-status";
    }

    @Override
    public boolean isAuditRequired() {
        return auditRequired;
    }
}
