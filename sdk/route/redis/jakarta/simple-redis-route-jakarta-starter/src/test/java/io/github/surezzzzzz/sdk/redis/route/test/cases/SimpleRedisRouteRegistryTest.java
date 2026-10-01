package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.factory.DefaultRedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.test.factory.MockRedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.validator.RedisRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;

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
        ConfigurationException ex = assertThrows(ConfigurationException.class,
                () -> createMockRegistry(factoryFactory, properties()),
                "创建失败时应抛 ConfigurationException");
        log.info("errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_006, ex.getErrorCode(), "errorCode 应为 REDIS_ROUTE_006");
        assertTrue(ex.getMessage().contains("cache"), "消息应包含失败 datasource key");
        assertNotNull(ex.getCause(), "应保留原始 cause");
        assertTrue(factoryFactory.getFactories().get("default").isDestroyed(),
                "创建失败后已创建的 factory 应被 destroy");
    }

    @Test
    public void testDatasourceCreateFailureWithDatasourceKeyInMessage() {
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        factoryFactory.setFailDatasourceKey("analytics");
        SimpleRedisRouteProperties props = propertiesWithThree();
        ConfigurationException ex = assertThrows(ConfigurationException.class,
                () -> createMockRegistry(factoryFactory, props),
                "指定 datasource 创建失败时应抛 ConfigurationException");
        log.info("errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertTrue(ex.getMessage().contains("analytics"), "消息应包含失败的 datasource key=analytics");
    }

    @Test
    public void testServerInfoProbeDisabledReturnsKnownFalse() {
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        SimpleRedisRouteProperties props = singleSource();
        props.getProbe().setServerInfo(false);
        SimpleRedisRouteRegistry registry = createMockRegistry(factoryFactory, props);

        RedisServerInfo info = registry.getServerInfo("default");
        log.info("known={}, errorMessage={}", info.isKnown(), info.getErrorMessage());
        assertNotNull(info, "serverInfo 不应为 null");
        assertFalse(info.isKnown(), "probe 禁用时 known 应为 false");
        assertNotNull(info.getErrorMessage(), "probe 禁用时应有 errorMessage");
    }

    @Test
    public void testGetServerInfosReturnsUnmodifiableView() {
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        SimpleRedisRouteRegistry registry = createMockRegistry(factoryFactory, singleSource());
        Map<String, RedisServerInfo> infos = registry.getServerInfos();
        log.info("serverInfos.size={}", infos.size());
        assertNotNull(infos, "getServerInfos 不应返回 null");
        assertEquals(1, infos.size(), "应有一个 datasource 的 serverInfo");
        assertThrows(UnsupportedOperationException.class,
                () -> infos.put("hack", null), "返回的 map 应不可修改");
    }

    @Test
    public void testGetDefaultDatasourceKey() {
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        SimpleRedisRouteRegistry registry = createMockRegistry(factoryFactory, singleSource());
        log.info("defaultDatasourceKey={}", registry.getDefaultDatasourceKey());
        assertEquals("default", registry.getDefaultDatasourceKey(), "默认 datasource key 应为 default");
    }

    @Test
    public void testConnectionFactoryFactoryReturningNullThrowsWithDatasourceKey() {
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        factoryFactory.setNullDatasourceKey("cache");
        ConfigurationException ex = assertThrows(ConfigurationException.class,
                () -> createMockRegistry(factoryFactory, properties()),
                "factory 返回 null 时应抛 ConfigurationException");
        log.info("errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_006, ex.getErrorCode(), "errorCode 应为 REDIS_ROUTE_006");
        assertTrue(ex.getMessage().contains("cache"), "消息应包含失败的 datasource key");
        // default 已创建，失败时也应被 destroy
        assertTrue(factoryFactory.getFactories().get("default").isDestroyed(),
                "factory 返回 null 时已创建的 default 也应被 destroy");
    }

    @Test
    public void testProbeFailureDoesNotBlockStartup() {
        // MockRedisConnectionFactory.getConnection() 抛 UnsupportedOperationException，
        // probe 应捕获并返回 known=false，但不阻断 registry 初始化
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        SimpleRedisRouteProperties props = singleSource();
        SimpleRedisRouteRegistry registry = assertDoesNotThrow(
                () -> createMockRegistry(factoryFactory, props),
                "probe 失败不应阻断启动");

        RedisServerInfo info = registry.getServerInfo("default");
        log.info("probe 失败后 known={}, errorMessage={}", info.isKnown(), info.getErrorMessage());
        assertFalse(info.isKnown(), "mock 连接不支持 INFO，应 known=false");
        assertNotNull(info.getErrorMessage(), "probe 失败应有 errorMessage");
        // registry 仍可正常获取 template
        assertNotNull(registry.getStringRedisTemplate("default"), "probe 失败后 template 仍应可用");
    }

    @Test
    public void testGetServerInfoForUnknownDatasourceThrows() {
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        SimpleRedisRouteRegistry registry = createMockRegistry(factoryFactory, singleSource());
        RouteException ex = assertThrows(RouteException.class,
                () -> registry.getServerInfo("non-existent"),
                "未注册 datasource 应抛 RouteException");
        log.info("errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_003, ex.getErrorCode(), "errorCode 应为 REDIS_ROUTE_003");
        assertTrue(ex.getMessage().contains("non-existent"), "消息应包含查询的 datasource key");
    }

    @Test
    public void testServerInfoDatasourceKeyMatchesRegistryKey() {
        MockRedisConnectionFactoryFactory factoryFactory = new MockRedisConnectionFactoryFactory();
        SimpleRedisRouteProperties props = properties();
        SimpleRedisRouteRegistry registry = createMockRegistry(factoryFactory, props);

        for (String key : registry.getDatasourceKeys()) {
            RedisServerInfo info = registry.getServerInfo(key);
            log.info("registry key=[{}] -> serverInfo.datasourceKey=[{}]", key, info.getDatasourceKey());
            assertEquals(key, info.getDatasourceKey(),
                    "serverInfo.datasourceKey 必须与 registry 中的 key 一致");
        }
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

    private SimpleRedisRouteProperties propertiesWithThree() {
        SimpleRedisRouteProperties properties = new SimpleRedisRouteProperties();
        properties.getSources().put("default", source(0));
        properties.getSources().put("cache", source(1));
        properties.getSources().put("analytics", source(2));
        return properties;
    }

    private SimpleRedisRouteProperties singleSource() {
        SimpleRedisRouteProperties properties = new SimpleRedisRouteProperties();
        properties.getSources().put("default", source(0));
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
