package io.github.surezzzzzz.sdk.redis.route.factory;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * RedisConnectionFactory 工厂
 *
 * @author surezzzzzz
 */
public interface RedisConnectionFactoryFactory {

    RedisConnectionFactory create(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config);
}
