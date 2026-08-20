package io.github.surezzzzzz.sdk.ops.middleware.mysql.query;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * MySQL 受控单条 SELECT 请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlSelectRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String sql;
    private final int size;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.MYSQL_SELECT;
    }

    @Override
    public String getResourceScope() {
        return "controlled-select";
    }
}
