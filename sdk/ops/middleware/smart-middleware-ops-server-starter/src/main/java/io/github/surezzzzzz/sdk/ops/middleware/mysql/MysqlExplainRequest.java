package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * MySQL 受控 EXPLAIN 请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlExplainRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String sql;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.MYSQL_EXPLAIN;
    }

    @Override
    public String getResourceScope() {
        return "controlled-explain";
    }
}
