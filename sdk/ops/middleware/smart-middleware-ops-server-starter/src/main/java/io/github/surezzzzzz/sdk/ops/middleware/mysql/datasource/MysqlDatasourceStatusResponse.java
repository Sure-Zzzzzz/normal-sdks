package io.github.surezzzzzz.sdk.ops.middleware.mysql.datasource;

import lombok.Builder;
import lombok.Getter;

/**
 * MySQL 数据源的安全状态投影。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MysqlDatasourceStatusResponse {

    private final String datasourceKey;
    private final String database;
    private final Boolean connected;
    private final Long durationMillis;
    private final String serverVersion;
    private final Boolean readOnly;
    private final Boolean superReadOnly;
}
