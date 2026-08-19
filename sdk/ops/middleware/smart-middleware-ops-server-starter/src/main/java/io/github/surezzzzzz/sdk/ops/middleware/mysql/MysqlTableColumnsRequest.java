package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * MySQL 精确表或视图列目录请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlTableColumnsRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String table;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.MYSQL_TABLE_COLUMNS;
    }

    @Override
    public String getResourceScope() {
        return "table-columns:" + table;
    }
}
