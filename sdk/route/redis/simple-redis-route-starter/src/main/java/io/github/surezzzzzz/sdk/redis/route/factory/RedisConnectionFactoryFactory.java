package io.github.surezzzzzz.sdk.redis.route.factory;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * RedisConnectionFactory 工厂
 *
 * @author surezzzzzz
 */
public interface RedisConnectionFactoryFactory {

    /**
     * 为指定 Route 数据源创建连接工厂。
     *
     * @param datasourceKey 数据源 key
     * @param config        数据源配置
     * @return 已完成初始化的连接工厂
     */
    RedisConnectionFactory create(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config);
}
