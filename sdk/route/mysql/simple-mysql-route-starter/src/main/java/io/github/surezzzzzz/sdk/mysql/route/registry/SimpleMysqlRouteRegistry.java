package io.github.surezzzzzz.sdk.mysql.route.registry;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.credential.MySqlRouteCredentialResolver;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteCredential;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import io.github.surezzzzzz.sdk.mysql.route.support.MySqlRouteStringHelper;
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
     * 校验并初始化全部固定物理数据源。
     *
     * @param properties         Route 配置
     * @param validator          配置校验器
     * @param credentialResolver 凭据解析 SPI
     * @param dataSourceFactory  物理数据源工厂
     */
    public SimpleMysqlRouteRegistry(SimpleMysqlRouteProperties properties,
                                    MySqlRoutePropertiesValidator validator,
                                    MySqlRouteCredentialResolver credentialResolver,
                                    MySqlRouteDataSourceFactory dataSourceFactory) {
        if (credentialResolver == null) {
            throw new ConfigurationException(ErrorCode.CONFIG_INVALID, ErrorMessage.CREDENTIAL_RESOLVER_REQUIRED);
        }
        this.dataSourceFactory = dataSourceFactory;
        validator.validate(properties);
        initialize(properties, credentialResolver);
    }

    /**
     * 获取已注册目标的物理数据源。
     *
     * @param datasourceKey 数据源键
     * @return 对应物理数据源
     */
    public DataSource getDataSource(String datasourceKey) {
        ensureActive();
        DataSource dataSource = dataSources.get(datasourceKey);
        if (dataSource == null) {
            throw new SimpleMysqlRouteException(ErrorCode.DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey));
        }
        return dataSource;
    }

    /**
     * 获取已注册目标的显式 JdbcTemplate。
     *
     * @param datasourceKey 数据源键
     * @return 对应 JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate(String datasourceKey) {
        ensureActive();
        JdbcTemplate jdbcTemplate = jdbcTemplates.get(datasourceKey);
        if (jdbcTemplate == null) {
            throw new SimpleMysqlRouteException(ErrorCode.DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey));
        }
        return jdbcTemplate;
    }

    /**
     * 获取已注册目标的安全逻辑元数据。
     *
     * @param datasourceKey 数据源键
     * @return 目标安全逻辑元数据
     */
    public MySqlRouteTarget getTarget(String datasourceKey) {
        ensureActive();
        MySqlRouteTarget target = targets.get(datasourceKey);
        if (target == null) {
            throw new SimpleMysqlRouteException(ErrorCode.DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey));
        }
        return target;
    }

    /**
     * 获取全部已注册的数据源键。
     *
     * @return 只读数据源键集合
     */
    public Set<String> getDatasourceKeys() {
        ensureActive();
        return Collections.unmodifiableSet(dataSources.keySet());
    }

    /**
     * 判断当前注册表是否仍包含指定数据源。
     *
     * @param datasourceKey 数据源键
     * @return 注册表存活且包含该键时返回 true
     */
    public boolean containsDatasource(String datasourceKey) {
        return !destroyed && dataSources.containsKey(datasourceKey);
    }

    /**
     * 生成供 Spring 路由数据源初始化使用的只读目标映射。
     *
     * @return 数据源键到物理数据源的只读映射
     */
    public Map<Object, Object> routingTargets() {
        ensureActive();
        Map<Object, Object> result = new LinkedHashMap<>();
        result.putAll(dataSources);
        return Collections.unmodifiableMap(result);
    }

    /**
     * 逆初始化顺序关闭由本注册表创建的物理数据源。
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
                log.warn("MySQL Route datasource 关闭失败，datasourceKey={}，异常类型={}",
                        entry.getKey(), e.getClass().getSimpleName());
            }
        }
        dataSources.clear();
        jdbcTemplates.clear();
        targets.clear();
    }

    private void initialize(SimpleMysqlRouteProperties properties, MySqlRouteCredentialResolver credentialResolver) {
        try {
            for (Map.Entry<String, SimpleMysqlRouteProperties.DatasourceConfig> entry
                    : properties.getDatasources().entrySet()) {
                String datasourceKey = entry.getKey();
                SimpleMysqlRouteProperties.DatasourceConfig datasourceConfig = entry.getValue();
                SimpleMysqlRouteProperties.ClusterConfig cluster = properties.getClusters()
                        .get(datasourceConfig.getClusterKey());
                MySqlRouteTarget target = new MySqlRouteTarget(datasourceKey, datasourceConfig.getClusterKey(),
                        datasourceConfig.getDatabase());
                MySqlRouteCredential credential = credentialResolver.resolve(cluster.getCredentialRef());
                if (credential == null || !MySqlRouteStringHelper.hasText(credential.getUsername())) {
                    throw new ConfigurationException(ErrorCode.CONFIG_INVALID,
                            String.format(ErrorMessage.CREDENTIAL_INVALID, target.getClusterKey()));
                }
                DataSource dataSource = null;
                try {
                    dataSource = dataSourceFactory.create(target, cluster, credential);
                    if (dataSource == null) {
                        throw new ConfigurationException(ErrorCode.DATASOURCE_CREATE_FAILED,
                                String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey));
                    }
                    dataSourceFactory.verify(dataSource);
                    dataSources.put(datasourceKey, dataSource);
                } catch (RuntimeException e) {
                    closeCurrent(dataSource, datasourceKey);
                    throw e;
                }
                jdbcTemplates.put(datasourceKey, new JdbcTemplate(dataSource));
                targets.put(datasourceKey, target);
            }
        } catch (ConfigurationException e) {
            destroy();
            throw e;
        } catch (RuntimeException e) {
            destroy();
            throw new ConfigurationException(ErrorCode.DATASOURCE_CREATE_FAILED,
                    ErrorMessage.TARGET_INITIALIZE_FAILED, e);
        }
    }

    private void closeCurrent(DataSource dataSource, String datasourceKey) {
        if (dataSource == null || dataSources.containsValue(dataSource)) {
            return;
        }
        try {
            dataSourceFactory.close(dataSource);
        } catch (RuntimeException e) {
            log.warn("MySQL Route 本地 datasource 关闭失败，datasourceKey={}，异常类型={}",
                    datasourceKey, e.getClass().getSimpleName());
        }
    }

    private void ensureActive() {
        if (destroyed) {
            throw new SimpleMysqlRouteException(ErrorCode.REGISTRY_DESTROYED, ErrorMessage.REGISTRY_DESTROYED);
        }
    }
}
