package io.github.surezzzzzz.sdk.redis.route.test.factory;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.factory.RedisConnectionFactoryFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用 RedisConnectionFactoryFactory
 *
 * @author surezzzzzz
 */
public class MockRedisConnectionFactoryFactory implements RedisConnectionFactoryFactory {

    private final Map<String, MockRedisConnectionFactory> factories = new LinkedHashMap<>();
    private String failDatasourceKey;

    @Override
    public RedisConnectionFactory create(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config) {
        if (datasourceKey.equals(failDatasourceKey)) {
            throw new IllegalStateException("mock create failed: " + datasourceKey);
        }
        MockRedisConnectionFactory factory = new MockRedisConnectionFactory(datasourceKey);
        factories.put(datasourceKey, factory);
        return factory;
    }

    public Map<String, MockRedisConnectionFactory> getFactories() {
        return factories;
    }

    public void setFailDatasourceKey(String failDatasourceKey) {
        this.failDatasourceKey = failDatasourceKey;
    }
}
