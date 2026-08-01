package io.github.surezzzzzz.sdk.mysql.route.model;

import lombok.Getter;

/**
 * 已校验的 MySQL Route 目标安全模型。
 *
 * @author surezzzzzz
 */
@Getter
public final class MySqlRouteTarget {

    private final String datasourceKey;
    private final String clusterKey;
    private final String database;

    public MySqlRouteTarget(String datasourceKey, String clusterKey, String database) {
        this.datasourceKey = datasourceKey;
        this.clusterKey = clusterKey;
        this.database = database;
    }
}
