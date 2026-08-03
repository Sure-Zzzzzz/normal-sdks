package io.github.surezzzzzz.sdk.mysql.route.datasource;

import com.zaxxer.hikari.HikariDataSource;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * 默认 MySQL Route DataSource 工厂。
 *
 * @author surezzzzzz
 */
public class DefaultMySqlRouteDataSourceFactory implements MySqlRouteDataSourceFactory {

    /**
     * 跨支持矩阵稳定的 Hikari 标量配置；连接身份和对象型扩展不允许从此处覆盖。
     */
    private static final Set<String> SUPPORTED_HIKARI_PROPERTIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            SimpleMysqlRouteConstant.HIKARI_CONNECTION_TIMEOUT_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_VALIDATION_TIMEOUT_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_CONNECTION_TEST_QUERY_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_CONNECTION_INIT_SQL_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_MAXIMUM_POOL_SIZE_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_MINIMUM_IDLE_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_IDLE_TIMEOUT_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_MAX_LIFETIME_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_INITIALIZATION_FAIL_TIMEOUT_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_AUTO_COMMIT_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_READ_ONLY_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_TRANSACTION_ISOLATION_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_CATALOG_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_SCHEMA_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_ISOLATE_INTERNAL_QUERIES_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_ALLOW_POOL_SUSPENSION_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_POOL_NAME_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_LEAK_DETECTION_THRESHOLD_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_REGISTER_MBEANS_PROPERTY,
            SimpleMysqlRouteConstant.HIKARI_EXCEPTION_OVERRIDE_CLASS_NAME_PROPERTY)));

    /**
     * 按目标的完整连接定义创建独立 Hikari 数据源。
     *
     * @param target     已校验的逻辑目标
     * @param datasource 完整连接定义
     * @return 新创建的物理数据源
     */
    @Override
    public DataSource create(MySqlRouteTarget target, SimpleMysqlRouteProperties.DatasourceConfig datasource) {
        HikariDataSource dataSource = new HikariDataSource();
        try {
            dataSource.setDriverClassName(datasource.getDriverClassName());
            dataSource.setJdbcUrl(datasource.getUrl());
            dataSource.setUsername(datasource.getUsername());
            dataSource.setPassword(datasource.getPassword());
            bindHikariProperties(target, datasource.getHikari(), dataSource);
            validateHikariConfiguration(target, dataSource);
            return dataSource;
        } catch (RuntimeException e) {
            dataSource.close();
            throw e;
        }
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
                throw new ConfigurationException(ErrorCode.DATASOURCE_VERIFY_FAILED,
                        ErrorMessage.DATASOURCE_UNAVAILABLE);
            }
        } catch (SQLException e) {
            throw new ConfigurationException(ErrorCode.DATASOURCE_VERIFY_FAILED,
                    ErrorMessage.DATASOURCE_VERIFY_FAILED);
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
                throw new SimpleMysqlRouteException(ErrorCode.DATASOURCE_CLOSE_FAILED,
                        ErrorMessage.DATASOURCE_CLOSE_FAILED);
            }
        }
    }

    private void bindHikariProperties(MySqlRouteTarget target, Map<String, String> hikari,
                                      HikariDataSource dataSource) {
        if (hikari == null || hikari.isEmpty()) {
            return;
        }
        BeanWrapper beanWrapper = new BeanWrapperImpl(dataSource);
        for (Map.Entry<String, String> entry : hikari.entrySet()) {
            if (!hasText(entry.getKey()) || !hasText(entry.getValue())) {
                throw invalidHikariConfiguration(target);
            }
            String propertyName = propertyName(entry.getKey());
            String driverPropertyName = driverPropertyName(entry.getKey());
            try {
                if (driverPropertyName != null) {
                    dataSource.addDataSourceProperty(driverPropertyName, entry.getValue());
                } else if (SUPPORTED_HIKARI_PROPERTIES.contains(propertyName)) {
                    beanWrapper.setPropertyValue(propertyName, entry.getValue());
                } else {
                    throw invalidHikariConfiguration(target);
                }
            } catch (RuntimeException e) {
                if (e instanceof ConfigurationException) {
                    throw e;
                }
                throw invalidHikariConfiguration(target);
            }
        }
    }

    private void validateHikariConfiguration(MySqlRouteTarget target, HikariDataSource dataSource) {
        try {
            if (dataSource.getMinimumIdle() > dataSource.getMaximumPoolSize()) {
                throw invalidHikariConfiguration(target);
            }
            dataSource.validate();
        } catch (RuntimeException e) {
            if (e instanceof ConfigurationException) {
                throw e;
            }
            throw invalidHikariConfiguration(target);
        }
    }

    private String driverPropertyName(String propertyKey) {
        int separatorIndex = propertyKey.indexOf('.');
        if (separatorIndex <= 0 || separatorIndex == propertyKey.length() - 1) {
            return null;
        }
        String prefix = propertyName(propertyKey.substring(0, separatorIndex));
        String driverPropertyName = propertyKey.substring(separatorIndex + 1);
        return SimpleMysqlRouteConstant.HIKARI_DATA_SOURCE_PROPERTIES_PREFIX.substring(0,
                SimpleMysqlRouteConstant.HIKARI_DATA_SOURCE_PROPERTIES_PREFIX.length() - 1).equals(prefix)
                && hasText(driverPropertyName) ? driverPropertyName : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String propertyName(String value) {
        StringBuilder result = new StringBuilder();
        boolean uppercase = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '-' || current == '_') {
                uppercase = true;
                continue;
            }
            result.append(uppercase ? Character.toUpperCase(current) : current);
            uppercase = false;
        }
        return result.toString();
    }

    private ConfigurationException invalidHikariConfiguration(MySqlRouteTarget target) {
        return new ConfigurationException(ErrorCode.HIKARI_CONFIGURATION_INVALID,
                String.format(ErrorMessage.HIKARI_CONFIGURATION_INVALID, target.getDatasource()));
    }
}
