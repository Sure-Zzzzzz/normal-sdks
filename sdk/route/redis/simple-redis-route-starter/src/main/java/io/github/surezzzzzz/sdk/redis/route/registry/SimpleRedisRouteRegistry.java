package io.github.surezzzzzz.sdk.redis.route.registry;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.factory.RedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.support.RedisRouteStringHelper;
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
    private volatile boolean destroyed = false;

    public SimpleRedisRouteRegistry(SimpleRedisRouteProperties properties,
                                    RedisRoutePropertiesValidator validator,
                                    RedisConnectionFactoryFactory factoryFactory) {
        this.properties = properties;
        validator.validate(properties);
        initialize(factoryFactory);
    }

    public RedisConnectionFactory getConnectionFactory() {
        return getConnectionFactory(properties.getDefaultSource());
    }

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

    public StringRedisTemplate getStringRedisTemplate() {
        return getStringRedisTemplate(properties.getDefaultSource());
    }

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

    public Set<String> getDatasourceKeys() {
        return Collections.unmodifiableSet(connectionFactories.keySet());
    }

    public boolean containsDatasource(String datasourceKey) {
        return connectionFactories.containsKey(datasourceKey);
    }

    private void initialize(RedisConnectionFactoryFactory factoryFactory) {
        try {
            for (Map.Entry<String, SimpleRedisRouteProperties.DataSourceConfig> entry : properties.getSources().entrySet()) {
                String datasourceKey = entry.getKey();
                RedisConnectionFactory factory = factoryFactory.create(datasourceKey, entry.getValue());
                StringRedisTemplate template = new StringRedisTemplate(factory);
                template.afterPropertiesSet();
                connectionFactories.put(datasourceKey, factory);
                stringRedisTemplates.put(datasourceKey, template);
                log.info("Redis route datasource 初始化完成，datasource=[{}]，mode=[{}]", datasourceKey, entry.getValue().getMode());
            }
        } catch (RuntimeException e) {
            destroyCreatedFactories();
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_006, ErrorMessage.CONFIG_SOURCES_EMPTY, e);
        } catch (Exception e) {
            destroyCreatedFactories();
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_006, ErrorMessage.CONFIG_SOURCES_EMPTY, e);
        }
    }

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
                    log.warn("Redis route datasource 关闭失败，datasource=[{}]", entry.getKey(), e);
                }
            }
        }
        stringRedisTemplates.clear();
        connectionFactories.clear();
    }
}
