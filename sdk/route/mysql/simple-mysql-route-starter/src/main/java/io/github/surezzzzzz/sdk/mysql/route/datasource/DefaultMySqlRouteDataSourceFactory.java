package io.github.surezzzzzz.sdk.mysql.route.datasource;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteCredential;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * 默认 MySQL Route DataSource 工厂。
 *
 * @author surezzzzzz
 */
public class DefaultMySqlRouteDataSourceFactory implements MySqlRouteDataSourceFactory {

    /**
     * 按目标的固定数据库创建独立物理数据源。
     *
     * @param target     已校验的逻辑目标
     * @param cluster    连接定义
     * @param credential 受控凭据
     * @return 新创建的物理数据源
     */
    @Override
    public DataSource create(MySqlRouteTarget target, SimpleMysqlRouteProperties.ClusterConfig cluster,
                             MySqlRouteCredential credential) {
        DataSourceBuilder<?> builder = DataSourceBuilder.create().driverClassName(cluster.getDriverClassName())
                .url(buildUrl(target, cluster, cluster.getConnectionProperties()))
                .username(credential.getUsername()).password(credential.getPassword());
        return builder.build();
    }

    /**
     * 获取连接并验证物理数据源可用。
     *
     * @param dataSource 待验证的数据源
     */
    @Override
    public void verify(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(SimpleMysqlRouteConstant.CONNECTION_VALIDATION_TIMEOUT_SECONDS)) {
                throw new IllegalStateException(ErrorMessage.DATASOURCE_UNAVAILABLE);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(ErrorMessage.DATASOURCE_VERIFY_FAILED, e);
        }
    }

    /**
     * 关闭实现了关闭契约的物理数据源。
     *
     * @param dataSource 待关闭的数据源
     */
    @Override
    public void close(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
                throw new IllegalStateException(ErrorMessage.DATASOURCE_CLOSE_FAILED, e);
            }
        }
    }

    private String buildUrl(MySqlRouteTarget target, SimpleMysqlRouteProperties.ClusterConfig cluster,
                            Map<String, String> properties) {
        StringBuilder url = new StringBuilder(SimpleMysqlRouteConstant.JDBC_URL_PREFIX).append(cluster.getHost()).append(':')
                .append(cluster.getPort()).append('/').append(target.getDatabase());
        if (properties != null && !properties.isEmpty()) {
            url.append(SimpleMysqlRouteConstant.JDBC_URL_QUERY_START);
            boolean first = true;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!first) {
                    url.append(SimpleMysqlRouteConstant.JDBC_URL_QUERY_SEPARATOR);
                }
                first = false;
                url.append(encode(entry.getKey())).append(SimpleMysqlRouteConstant.JDBC_URL_KEY_VALUE_SEPARATOR).append(encode(entry.getValue()));
            }
        }
        return url.toString();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, SimpleMysqlRouteConstant.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(ErrorMessage.UTF_8_UNAVAILABLE, e);
        }
    }
}
