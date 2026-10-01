package io.github.surezzzzzz.sdk.mysql.route.registry;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import io.github.surezzzzzz.sdk.mysql.route.validator.MySqlRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.*;

/**
 * MySQL Route 目标注册表。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleMysqlRouteRegistry implements DisposableBean {

    private final MySqlRouteDataSourceFactory dataSourceFactory;
    private final Map<String, DataSource> dataSources = new LinkedHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new LinkedHashMap<>();
    private final Map<String, MySqlRouteTarget> targets = new LinkedHashMap<>();
    private volatile boolean destroyed;

    /**
     * 使用 Route 管理的物理数据源创建注册表。
     *
     * @param properties        MySQL Route 配置
     * @param validator         配置校验器
     * @param dataSourceFactory 物理数据源工厂
     */
    public SimpleMysqlRouteRegistry(SimpleMysqlRouteProperties properties,
                                    MySqlRoutePropertiesValidator validator,
                                    MySqlRouteDataSourceFactory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
        validator.validate(properties);
        initialize(properties);
    }

    /**
     * 获取指定目标的物理数据源。
     *
     * @param datasource 调用方配置的数据源名称
     * @return 物理数据源
     */
    public DataSource getDataSource(String datasource) {
        ensureActive();
        DataSource dataSource = dataSources.get(datasource);
        if (dataSource == null) {
            throw new SimpleMysqlRouteException(ErrorCode.DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasource));
        }
        return dataSource;
    }

    /**
     * 获取指定目标的 JdbcTemplate。
     *
     * @param datasource 调用方配置的数据源名称
     * @return 目标 JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate(String datasource) {
        ensureActive();
        JdbcTemplate jdbcTemplate = jdbcTemplates.get(datasource);
        if (jdbcTemplate == null) {
            throw new SimpleMysqlRouteException(ErrorCode.DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasource));
        }
        return jdbcTemplate;
    }

    /**
     * 获取指定目标的安全元数据。
     *
     * @param datasource 调用方配置的数据源名称
     * @return 目标安全元数据
     */
    public MySqlRouteTarget getTarget(String datasource) {
        ensureActive();
        MySqlRouteTarget target = targets.get(datasource);
        if (target == null) {
            throw new SimpleMysqlRouteException(ErrorCode.DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasource));
        }
        return target;
    }

    /**
     * 获取全部已登记的数据源名称。
     *
     * @return 不可修改的数据源名称集合
     */
    public Set<String> getDatasources() {
        ensureActive();
        return Collections.unmodifiableSet(dataSources.keySet());
    }

    /**
     * 判断指定数据源名称是否已登记。
     *
     * @param datasource 调用方配置的数据源名称
     * @return 是否已登记
     */
    public boolean containsDatasource(String datasource) {
        return !destroyed && dataSources.containsKey(datasource);
    }

    /**
     * 获取供 RoutingDataSource 初始化使用的目标映射。
     *
     * @return 不可修改的目标映射
     */
    public Map<Object, Object> routingTargets() {
        ensureActive();
        Map<Object, Object> result = new LinkedHashMap<>();
        result.putAll(dataSources);
        return Collections.unmodifiableMap(result);
    }

    /**
     * 逆序关闭全部 Route 管理的物理数据源。
     */
    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        List<Map.Entry<String, DataSource>> entries = new ArrayList<>(dataSources.entrySet());
        Collections.reverse(entries);
        for (Map.Entry<String, DataSource> entry : entries) {
            try {
                dataSourceFactory.close(entry.getValue());
            } catch (RuntimeException e) {
                log.warn("MySQL Route datasource 关闭失败，datasource={}，异常类型={}",
                        entry.getKey(), e.getClass().getSimpleName());
            }
        }
        dataSources.clear();
        jdbcTemplates.clear();
        targets.clear();
    }

    private void initialize(SimpleMysqlRouteProperties properties) {
        try {
            for (Map.Entry<String, SimpleMysqlRouteProperties.DatasourceConfig> entry
                    : properties.getDatasources().entrySet()) {
                registerTarget(entry.getKey(), entry.getValue());
            }
        } catch (ConfigurationException e) {
            destroy();
            throw e;
        } catch (RuntimeException e) {
            destroy();
            throw new ConfigurationException(ErrorCode.DATASOURCE_CREATE_FAILED,
                    ErrorMessage.TARGET_INITIALIZE_FAILED);
        }
    }

    private void registerTarget(String datasource, SimpleMysqlRouteProperties.DatasourceConfig datasourceConfig) {
        MySqlRouteTarget target = new MySqlRouteTarget(datasource);
        DataSource dataSource = null;
        try {
            dataSource = dataSourceFactory.create(target, datasourceConfig);
            if (dataSource == null || containsDataSource(dataSource)) {
                throw new ConfigurationException(ErrorCode.DATASOURCE_CREATE_FAILED,
                        String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasource));
            }
            dataSourceFactory.verify(dataSource);
            dataSources.put(datasource, dataSource);
            jdbcTemplates.put(datasource, new JdbcTemplate(dataSource));
            targets.put(datasource, target);
        } catch (RuntimeException e) {
            closeCurrent(dataSource, datasource);
            throw e;
        }
    }

    private boolean containsDataSource(DataSource dataSource) {
        for (DataSource registered : dataSources.values()) {
            if (registered == dataSource) {
                return true;
            }
        }
        return false;
    }

    private void closeCurrent(DataSource dataSource, String datasource) {
        if (dataSource == null || containsDataSource(dataSource)) {
            return;
        }
        try {
            dataSourceFactory.close(dataSource);
        } catch (RuntimeException e) {
            log.warn("MySQL Route 本地 datasource 关闭失败，datasource={}，异常类型={}",
                    datasource, e.getClass().getSimpleName());
        }
    }

    private void ensureActive() {
        if (destroyed) {
            throw new SimpleMysqlRouteException(ErrorCode.REGISTRY_DESTROYED, ErrorMessage.REGISTRY_DESTROYED);
        }
    }
}
