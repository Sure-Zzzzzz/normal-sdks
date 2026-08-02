package io.github.surezzzzzz.sdk.mysql.route.datasource;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;

import javax.sql.DataSource;

/**
 * MySQL Route 物理 DataSource 工厂。
 *
 * @author surezzzzzz
 */
public interface MySqlRouteDataSourceFactory {

    /**
     * 按固定逻辑目标、集群连接定义和目标连接定义创建物理数据源。
     *
     * @param target     已校验的逻辑目标
     * @param cluster    集群连接定义
     * @param datasource 目标连接定义
     * @return 新创建的物理数据源
     */
    DataSource create(MySqlRouteTarget target, SimpleMysqlRouteProperties.ClusterConfig cluster,
                      SimpleMysqlRouteProperties.DatasourceConfig datasource);

    /**
     * 验证物理数据源可用性。
     *
     * @param dataSource 待验证的数据源
     */
    void verify(DataSource dataSource);

    /**
     * 关闭由工厂创建的数据源。
     *
     * @param dataSource 待关闭的数据源
     */
    void close(DataSource dataSource);
}
