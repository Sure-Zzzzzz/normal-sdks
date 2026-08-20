package io.github.surezzzzzz.sdk.ops.middleware.mysql.table;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * MySQL 精确基表索引目录请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlTableIndexesRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String table;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.MYSQL_TABLE_INDEXES;
    }

    @Override
    public String getResourceScope() {
        return "table-indexes:" + table;
    }
}
