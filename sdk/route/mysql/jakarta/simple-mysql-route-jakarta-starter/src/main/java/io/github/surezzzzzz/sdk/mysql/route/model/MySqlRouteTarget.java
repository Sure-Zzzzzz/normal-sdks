package io.github.surezzzzzz.sdk.mysql.route.model;

import lombok.Getter;

/**
 * 已校验的 MySQL Route 目标安全模型。
 *
 * @author surezzzzzz
 */
@Getter
public final class MySqlRouteTarget {

    /**
     * 调用方配置的数据源名称。
     */
    private final String datasource;

    /**
     * 创建已校验的数据源目标。
     *
     * @param datasource 调用方配置的数据源名称
     */
    public MySqlRouteTarget(String datasource) {
        this.datasource = datasource;
    }
}
