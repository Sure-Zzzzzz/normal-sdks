package io.github.surezzzzzz.sdk.ops.middleware.mysql.table;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * MySQL 表与视图目录请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlTableListRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String prefix;
    private final int size;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.MYSQL_TABLE_LIST;
    }

    @Override
    public String getResourceScope() {
        return "table-list";
    }
}
