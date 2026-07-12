package io.github.surezzzzzz.sdk.redis.route.registry;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.factory.RedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.support.RedisRouteStringHelper;
import io.github.surezzzzzz.sdk.redis.route.support.RedisServerInfoProbeHelper;
import io.github.surezzzzzz.sdk.redis.route.validator.RedisRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis route 注册表
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleRedisRouteRegistry implements DisposableBean {

    private final SimpleRedisRouteProperties properties;
    private final Map<String, RedisConnectionFactory> connectionFactories = new LinkedHashMap<>();
    private final Map<String, StringRedisTemplate> stringRedisTemplates = new LinkedHashMap<>();
    private final Map<String, RedisServerInfo> serverInfos = new LinkedHashMap<>();
    private volatile boolean destroyed = false;

    /**
     * 构造 Redis route 注册表并初始化全部 datasource。
     *
     * @param properties     route 配置
     * @param validator      配置校验器
     * @param factoryFactory Redis 连接工厂创建器
     * @throws ConfigurationException 配置非法或 datasource 创建失败时抛出
     */
    public SimpleRedisRouteRegistry(SimpleRedisRouteProperties properties,
                                    RedisRoutePropertiesValidator validator,
                                    RedisConnectionFactoryFactory factoryFactory) {
        this.properties = properties;
        validator.validate(properties);
        initialize(factoryFactory);
        probeServerInfos();
    }

    /**
     * 获取默认 datasource 的连接工厂。
     *
     * @return 默认 datasource 的连接工厂
     */
    public RedisConnectionFactory getConnectionFactory() {
        return getConnectionFactory(properties.getDefaultSource());
    }

    /**
     * 获取指定 datasource 的连接工厂。
     *
     * @param datasourceKey datasource key
     * @return 指定 datasource 的连接工厂
     * @throws RouteException datasource key 为空或未注册时抛出
     */
    public RedisConnectionFactory getConnectionFactory(String datasourceKey) {
        if (!RedisRouteStringHelper.hasText(datasourceKey)) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_003,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, getDatasourceKeys()));
        }
        RedisConnectionFactory factory = connectionFactories.get(datasourceKey);
        if (factory == null) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_003,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, getDatasourceKeys()));
        }
        return factory;
    }

    /**
     * 获取默认 datasource 的 StringRedisTemplate。
     *
     * @return 默认 datasource 的 StringRedisTemplate
     */
    public StringRedisTemplate getStringRedisTemplate() {
        return getStringRedisTemplate(properties.getDefaultSource());
    }

    /**
     * 获取指定 datasource 的 StringRedisTemplate。
     *
     * @param datasourceKey datasource key
     * @return 指定 datasource 的 StringRedisTemplate
     * @throws RouteException datasource key 为空或未注册时抛出
     */
    public StringRedisTemplate getStringRedisTemplate(String datasourceKey) {
        if (!RedisRouteStringHelper.hasText(datasourceKey)) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_003,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, getDatasourceKeys()));
        }
        StringRedisTemplate template = stringRedisTemplates.get(datasourceKey);
        if (template == null) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_003,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, getDatasourceKeys()));
        }
        return template;
    }

    /**
     * 获取指定 datasource 的 Server 信息快照。
     * datasource 未注册时抛 RouteException（REDIS_ROUTE_003），与 getStringRedisTemplate 语义一致。
     *
     * @param datasourceKey datasource key
     * @return 指定 datasource 的 Server 信息快照
     * @throws RouteException datasource key 为空或未注册时抛出
     */
    public RedisServerInfo getServerInfo(String datasourceKey) {
        if (!RedisRouteStringHelper.hasText(datasourceKey)) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_003,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, getDatasourceKeys()));
        }
        RedisServerInfo info = serverInfos.get(datasourceKey);
        if (info == null) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_003,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, getDatasourceKeys()));
        }
        return info;
    }

    /**
     * 获取全部 datasource 的 Server 信息快照（只读视图）。
     *
     * @return 全部 datasource 的 Server 信息快照
     */
    public Map<String, RedisServerInfo> getServerInfos() {
        return Collections.unmodifiableMap(serverInfos);
    }

    /**
     * 获取已注册的 datasource key 集合。
     *
     * @return 已注册的 datasource key 集合
     */
    public Set<String> getDatasourceKeys() {
        return Collections.unmodifiableSet(connectionFactories.keySet());
    }

    /**
     * 判断 datasource 是否已注册。
     *
     * @param datasourceKey datasource key
     * @return true 表示已注册，false 表示未注册
     */
    public boolean containsDatasource(String datasourceKey) {
        return connectionFactories.containsKey(datasourceKey);
    }

    /**
     * 获取默认数据源 key。
     *
     * @return 默认数据源 key
     */
    public String getDefaultDatasourceKey() {
        return properties.getDefaultSource();
    }

    private void initialize(RedisConnectionFactoryFactory factoryFactory) {
        for (Map.Entry<String, SimpleRedisRouteProperties.DataSourceConfig> entry : properties.getSources().entrySet()) {
            String datasourceKey = entry.getKey();
            RedisConnectionFactory factory = null;
            try {
                factory = factoryFactory.create(datasourceKey, entry.getValue());
                if (factory == null) {
                    destroyCreatedFactories();
                    throw new ConfigurationException(ErrorCode.REDIS_ROUTE_006,
                            String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey));
                }
                StringRedisTemplate template = new StringRedisTemplate(factory);
                template.afterPropertiesSet();
                connectionFactories.put(datasourceKey, factory);
                stringRedisTemplates.put(datasourceKey, template);
                log.info("Redis route datasource 初始化完成，datasource=[{}]，mode=[{}]",
                        datasourceKey, entry.getValue().getMode());
            } catch (ConfigurationException e) {
                destroyLocalFactory(factory, datasourceKey);
                destroyCreatedFactories();
                throw e;
            } catch (RuntimeException e) {
                destroyLocalFactory(factory, datasourceKey);
                destroyCreatedFactories();
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_006,
                        String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey), e);
            } catch (Exception e) {
                destroyLocalFactory(factory, datasourceKey);
                destroyCreatedFactories();
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_006,
                        String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey), e);
            }
        }
    }

    /**
     * 探测所有 datasource 的 Redis Server 信息，失败不阻断启动
     */
    private void probeServerInfos() {
        boolean probeEnabled = properties.getProbe() != null && properties.getProbe().isServerInfo();
        for (Map.Entry<String, RedisConnectionFactory> entry : connectionFactories.entrySet()) {
            String datasourceKey = entry.getKey();
            RedisServerInfo info = RedisServerInfoProbeHelper.probe(datasourceKey, entry.getValue(), probeEnabled);
            serverInfos.put(datasourceKey, info);
        }
    }

    /**
     * 关闭 registry 持有的全部 Redis 连接工厂。
     */
    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        destroyCreatedFactories();
    }

    private void destroyCreatedFactories() {
        for (Map.Entry<String, RedisConnectionFactory> entry : connectionFactories.entrySet()) {
            RedisConnectionFactory factory = entry.getValue();
            if (factory instanceof DisposableBean) {
                try {
                    ((DisposableBean) factory).destroy();
                    log.info("Redis route datasource 已关闭，datasource=[{}]", entry.getKey());
                } catch (Exception e) {
                    log.warn("Redis route datasource 关闭失败，datasource=[{}]，异常类型=[{}]",
                            entry.getKey(), e.getClass().getSimpleName());
                }
            }
        }
        stringRedisTemplates.clear();
        connectionFactories.clear();
    }

    /**
     * 关闭当前循环中已创建但尚未登记到 connectionFactories 的 factory。
     * 已在 map 中的由 destroyCreatedFactories 统一处理，不重复关闭。
     */
    private void destroyLocalFactory(RedisConnectionFactory factory, String datasourceKey) {
        if (factory == null || connectionFactories.containsValue(factory)) {
            return;
        }
        if (factory instanceof DisposableBean) {
            try {
                ((DisposableBean) factory).destroy();
                log.info("Redis route 本地 datasource 已关闭，datasource=[{}]", datasourceKey);
            } catch (Exception e) {
                log.warn("Redis route 本地 datasource 关闭失败，datasource=[{}]，异常类型=[{}]",
                        datasourceKey, e.getClass().getSimpleName());
            }
        }
    }
}
