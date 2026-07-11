package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.factory.DefaultRedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.test.factory.MockRedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.validator.RedisRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis route 注册表测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleRedisRouteRegistryTest {

    @Test
    public void testRegisterDefaultAndNamedDatasourceWithRealRedis() throws Exception {
        SimpleRedisRouteRegistry registry = createRealRegistry(properties());

        assertTrue(registry.containsDatasource("default"));
        assertTrue(registry.containsDatasource("cache"));
        assertFalse(registry.containsDatasource("lock"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("default", "cache")), registry.getDatasourceKeys());
        assertSame(registry.getConnectionFactory("default"), registry.getConnectionFactory());
        assertSame(registry.getStringRedisTemplate("default"), registry.getStringRedisTemplate());
        assertNotSame(registry.getConnectionFactory(), registry.getConnectionFactory("cache"));
        assertNotSame(registry.getStringRedisTemplate(), registry.getStringRedisTemplate("cache"));

        registry.getStringRedisTemplate("cache").opsForValue().set("cache:registry:001", "registry-value");
        assertEquals("registry-value", registry.getStringRedisTemplate("cache").opsForValue().get("cache:registry:001"));
        assertNull(registry.getStringRedisTemplate().opsForValue().get("cache:registry:001"));
        registry.getStringRedisTemplate("cache").delete("cache:registry:001");

        registry.destroy();
        registry.destroy();
    }

    @Test
    public void testRollbackWhenCreateFailed() {
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        factoryFactory.setFailDatasourceKey("cache");
        assertThrows(ConfigurationException.class, () -> createMockRegistry(factoryFactory, properties()));
        assertTrue(factoryFactory.getFactories().get("default").isDestroyed());
    }

    private SimpleRedisRouteRegistry createRealRegistry(SimpleRedisRouteProperties properties) {
        return new SimpleRedisRouteRegistry(properties,
                new RedisRoutePropertiesValidator(new RedisRoutePatternMatcher()), new DefaultRedisConnectionFactoryFactory());
    }

    private SimpleRedisRouteRegistry createMockRegistry(MockRedisConnectionFactoryFactory factoryFactory,
                                                       SimpleRedisRouteProperties properties) {
        return new SimpleRedisRouteRegistry(properties,
                new RedisRoutePropertiesValidator(new RedisRoutePatternMatcher()), factoryFactory);
    }

    private SimpleRedisRouteProperties properties() {
        SimpleRedisRouteProperties properties = new SimpleRedisRouteProperties();
        properties.getSources().put("default", source(0));
        properties.getSources().put("cache", source(1));
        return properties;
    }

    private SimpleRedisRouteProperties.DataSourceConfig source(int database) {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setHost("localhost");
        config.setPort(6379);
        config.setDatabase(database);
        config.setTimeoutMs(3000L);
        config.setConnectTimeoutMs(3000L);
        return config;
    }
}
