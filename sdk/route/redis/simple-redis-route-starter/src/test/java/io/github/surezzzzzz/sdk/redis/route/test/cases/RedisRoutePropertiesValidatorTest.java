package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.RedisSourceMode;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.validator.RedisRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockPropertySource;

import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis route 配置校验测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisRoutePropertiesValidatorTest {

    private final RedisRoutePropertiesValidator validator = new RedisRoutePropertiesValidator(new RedisRoutePatternMatcher());

    @Test
    public void testValidMixedSources() {
        SimpleRedisRouteProperties properties = baseProperties();
        SimpleRedisRouteProperties.DataSourceConfig cluster = new SimpleRedisRouteProperties.DataSourceConfig();
        cluster.setMode(RedisSourceMode.CLUSTER.getCode());
        cluster.setNodes(Arrays.asList("localhost:7000", "localhost:7001"));
        properties.getSources().put("cache", cluster);

        properties.getRules().add(rule("cache:", "prefix", "cache", 1));

        assertDoesNotThrow(() -> validator.validate(properties));
    }

    @Test
    public void testDefaultSourceMissing() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.setDefaultSource("missing");
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));
        assertEquals(ErrorCode.REDIS_ROUTE_002, exception.getErrorCode());
    }

    @Test
    public void testClusterDatabaseMustBeZero() {
        SimpleRedisRouteProperties properties = baseProperties();
        SimpleRedisRouteProperties.DataSourceConfig cluster = new SimpleRedisRouteProperties.DataSourceConfig();
        cluster.setMode(RedisSourceMode.CLUSTER.getCode());
        cluster.setNodes(Arrays.asList("localhost:7000"));
        cluster.setDatabase(1);
        properties.getSources().put("cache", cluster);
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));
        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
    }

    @Test
    public void testLettuceRequestQueueSizeMustBePositive() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.getSources().get("default").getLettuce().setRequestQueueSize(0);
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));
        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("request-queue-size"));
    }

    @Test
    public void testClusterRefreshPeriodMustBePositive() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.getSources().get("default").getLettuce().setClusterRefreshPeriodMs(0L);
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));
        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("cluster-refresh-period-ms"));
    }

    @Test
    public void testLettucePoolBindsFromKebabCaseProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource()
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.pool.enabled", "true")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.pool.max-active", "32")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.pool.max-idle", "16")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.pool.min-idle", "4")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.pool.max-wait-ms", "1000")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.pool.time-between-eviction-runs-ms", "30000"));

        SimpleRedisRouteProperties properties = Binder.get(environment)
                .bind("io.github.surezzzzzz.sdk.redis.route", SimpleRedisRouteProperties.class)
                .orElseGet(SimpleRedisRouteProperties::new);
        SimpleRedisRouteProperties.PoolConfig pool = properties.getSources().get("default").getLettuce().getPool();

        assertTrue(pool.isEnabled());
        assertEquals(32, pool.getMaxActive());
        assertEquals(16, pool.getMaxIdle());
        assertEquals(4, pool.getMinIdle());
        assertEquals(1000L, pool.getMaxWaitMs());
        assertEquals(30000L, pool.getTimeBetweenEvictionRunsMs());
    }

    @Test
    public void testDisabledPoolDoesNotValidateInactiveCapacityValues() {
        SimpleRedisRouteProperties properties = baseProperties();
        SimpleRedisRouteProperties.PoolConfig pool = properties.getSources().get("default").getLettuce().getPool();
        pool.setMaxActive(0);
        pool.setMaxIdle(-1);
        pool.setMinIdle(-1);
        pool.setMaxWaitMs(-2L);

        assertDoesNotThrow(() -> validator.validate(properties));
    }

    @Test
    public void testEnabledPoolAcceptsStandaloneAndClusterSources() {
        SimpleRedisRouteProperties properties = baseProperties();
        enablePool(properties.getSources().get("default"));
        SimpleRedisRouteProperties.DataSourceConfig cluster = new SimpleRedisRouteProperties.DataSourceConfig();
        cluster.setMode(RedisSourceMode.CLUSTER.getCode());
        cluster.setNodes(Arrays.asList("localhost:7000", "localhost:7001"));
        enablePool(cluster);
        properties.getSources().put("cluster", cluster);

        assertDoesNotThrow(() -> validator.validate(properties));
    }

    @Test
    public void testEnabledPoolRejectsInvalidCapacityValuesWithoutConnectionDetails() {
        assertPoolValidationFailure("max-active", pool -> pool.setMaxActive(0));
        assertPoolValidationFailure("max-idle", pool -> pool.setMaxIdle(-1));
        assertPoolValidationFailure("min-idle", pool -> pool.setMinIdle(-1));
        assertPoolValidationFailure("min-idle", pool -> {
            pool.setMaxIdle(3);
            pool.setMinIdle(4);
        });
        assertPoolValidationFailure("max-wait-ms", pool -> pool.setMaxWaitMs(-2L));
        assertPoolValidationFailure("time-between-eviction-runs-ms", pool -> pool.setTimeBetweenEvictionRunsMs(-2L));
    }

    @Test
    public void testLettuceClusterOptionsBindFromKebabCaseProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource()
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.mode", "cluster")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.nodes[0]", "localhost:7000")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.ssl-verify-peer", "false")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.lettuce.cluster-dynamic-refresh-sources", "false")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.lettuce.cluster-close-stale-connections", "false")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.lettuce.read-from", "replica-preferred"));

        SimpleRedisRouteProperties properties = Binder.get(environment)
                .bind("io.github.surezzzzzz.sdk.redis.route", SimpleRedisRouteProperties.class)
                .orElseGet(SimpleRedisRouteProperties::new);
        SimpleRedisRouteProperties.DataSourceConfig config = properties.getSources().get("cluster");

        assertFalse(config.isSslVerifyPeer());
        assertFalse(config.getLettuce().isClusterDynamicRefreshSources());
        assertFalse(config.getLettuce().isClusterCloseStaleConnections());
        assertEquals("replica-preferred", config.getLettuce().getReadFrom());
    }

    @Test
    public void testStandaloneRejectsNonDefaultClusterOptions() {
        assertStandaloneClusterOptionRejected("read-from", config -> config.getLettuce().setReadFrom("replica"));
        assertStandaloneClusterOptionRejected("cluster-adaptive-refresh",
                config -> config.getLettuce().setClusterAdaptiveRefresh(false));
        assertStandaloneClusterOptionRejected("cluster-periodic-refresh",
                config -> config.getLettuce().setClusterPeriodicRefresh(false));
        assertStandaloneClusterOptionRejected("cluster-refresh-period-ms",
                config -> config.getLettuce().setClusterRefreshPeriodMs(30000L));
        assertStandaloneClusterOptionRejected("cluster-dynamic-refresh-sources",
                config -> config.getLettuce().setClusterDynamicRefreshSources(false));
        assertStandaloneClusterOptionRejected("cluster-close-stale-connections",
                config -> config.getLettuce().setClusterCloseStaleConnections(false));
    }

    @Test
    public void testReadFromMustBeKnownValue() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.getSources().get("default").getLettuce().setReadFrom("slave");

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));

        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("read-from"));
        assertTrue(exception.getMessage().contains("replica"));
    }

    @Test
    public void testClusterTopologyAddressFollowNodesBindsFromKebabCaseProperty() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource()
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.mode", "cluster")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.nodes[0]",
                        "redis-cluster-0.redis-cluster-headless.route-test:6379")
                .withProperty("io.github.surezzzzzz.sdk.redis.route.sources.cluster.cluster-topology-address-follow-nodes",
                        "true"));

        SimpleRedisRouteProperties properties = Binder.get(environment)
                .bind("io.github.surezzzzzz.sdk.redis.route", SimpleRedisRouteProperties.class)
                .orElseGet(SimpleRedisRouteProperties::new);

        assertTrue(properties.getSources().get("cluster").isClusterTopologyAddressFollowNodes());
    }

    @Test
    public void testClusterTopologyAddressFollowNodesIsAllowedForCluster() {
        SimpleRedisRouteProperties properties = baseProperties();
        SimpleRedisRouteProperties.DataSourceConfig cluster = new SimpleRedisRouteProperties.DataSourceConfig();
        cluster.setMode(RedisSourceMode.CLUSTER.getCode());
        cluster.setNodes(Arrays.asList("redis-cluster-0.redis-cluster-headless.route-test:6379"));
        cluster.setClusterTopologyAddressFollowNodes(true);
        properties.getSources().put("cluster", cluster);

        assertDoesNotThrow(() -> validator.validate(properties));
    }

    @Test
    public void testClusterTopologyAddressFollowNodesRejectsAmbiguousOrUnsupportedNodes() {
        SimpleRedisRouteProperties properties = baseProperties();
        SimpleRedisRouteProperties.DataSourceConfig cluster = new SimpleRedisRouteProperties.DataSourceConfig();
        cluster.setMode(RedisSourceMode.CLUSTER.getCode());
        cluster.setClusterTopologyAddressFollowNodes(true);
        cluster.setNodes(Arrays.asList("redis-cluster-0.redis-cluster-headless.route-test:6379",
                "redis-cluster-1.redis-cluster-headless.other:6379"));
        properties.getSources().put("cluster", cluster);

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));

        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("cluster-topology-address-follow-nodes"));
        assertFalse(exception.getMessage().contains("redis-cluster-0"));
    }

    @Test
    public void testClusterTopologyAddressFollowNodesRejectsNodesWithoutMapping() {
        SimpleRedisRouteProperties properties = baseProperties();
        SimpleRedisRouteProperties.DataSourceConfig cluster = new SimpleRedisRouteProperties.DataSourceConfig();
        cluster.setMode(RedisSourceMode.CLUSTER.getCode());
        cluster.setClusterTopologyAddressFollowNodes(true);
        cluster.setNodes(Arrays.asList("redis-cluster-0.redis-cluster-headless.route-test.svc.example:6379"));
        properties.getSources().put("cluster", cluster);

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));

        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("cluster-topology-address-follow-nodes"));
        assertFalse(exception.getMessage().contains("svc.example"));
    }

    @Test
    public void testClusterTopologyAddressFollowNodesIsNotAllowedForStandalone() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.getSources().get("default").setClusterTopologyAddressFollowNodes(true);
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));

        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("standalone"));
    }

    @Test
    public void testInvalidRegexContainsRuleContext() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.getRules().add(rule("[", "regex", "default", 1));
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties));
        assertEquals(ErrorCode.REDIS_ROUTE_004, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("第 0 条"));
        assertTrue(exception.getMessage().contains("pattern=[[]"));
        assertTrue(exception.getMessage().contains("type=[regex]"));
        assertTrue(exception.getMessage().contains("datasource=[default]"));
    }

    @Test
    public void testDatasourceConfigToStringDoesNotLeakConnectionDetails() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setHost("redis-internal.example.test");
        config.setPort(16379);
        config.setNodes(Arrays.asList("redis-node-a.example.test:16379"));
        config.setUsername("route-user");
        config.setPassword("opaque-credential-content");
        config.setClientName("route-client");

        String text = config.toString();

        assertFalse(text.contains("redis-internal.example.test"), "toString 不得包含 Redis 主机信息");
        assertFalse(text.contains("16379"), "toString 不得包含 Redis 端口信息");
        assertFalse(text.contains("redis-node-a.example.test"), "toString 不得包含 Redis 节点信息");
        assertFalse(text.contains("route-user"), "toString 不得包含 Redis 用户名");
        assertFalse(text.contains("opaque-credential-content"), "toString 不得包含 Redis 密码");
        assertFalse(text.contains("route-client"), "toString 不得包含 Redis 客户端名称");
    }

    @Test
    public void testDefaultSourceMustNotBeBlank() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.setDefaultSource("  ");
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties),
                "default-source 空白时应抛 ConfigurationException");
        log.info("errorCode={}, message={}", exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_002, exception.getErrorCode(), "errorCode 应为 REDIS_ROUTE_002");
        assertTrue(exception.getMessage().contains("default-source"), "消息应包含 default-source");
    }

    @Test
    public void testRouteDatasourceMustNotBeBlank() {
        SimpleRedisRouteProperties properties = baseProperties();
        SimpleRedisRouteProperties.RouteRule ruleWithBlankDs = rule("prefix:", "prefix", "  ", 1);
        properties.getRules().add(ruleWithBlankDs);
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties),
                "rule datasource 空白时应抛 ConfigurationException");
        log.info("errorCode={}, message={}", exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_004, exception.getErrorCode(), "errorCode 应为 REDIS_ROUTE_004");
        assertTrue(exception.getMessage().contains("第 0 条"), "消息应包含规则下标");
        assertTrue(exception.getMessage().contains("datasource"), "消息应包含 datasource");
    }

    @Test
    public void testRouteDatasourceMissingStillShowsConfiguredSources() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.getRules().add(rule("prefix:", "prefix", "nonexistent", 1));
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> validator.validate(properties),
                "datasource 不存在时应抛 ConfigurationException");
        log.info("errorCode={}, message={}", exception.getErrorCode(), exception.getMessage());
        assertTrue(exception.getMessage().contains("default"), "消息应包含已配置 datasource 列表");
    }

    private void enablePool(SimpleRedisRouteProperties.DataSourceConfig config) {
        config.getLettuce().getPool().setEnabled(true);
    }

    private void assertStandaloneClusterOptionRejected(String expectedProperty,
                                                       Consumer<SimpleRedisRouteProperties.DataSourceConfig> customizer) {
        SimpleRedisRouteProperties properties = baseProperties();
        customizer.accept(properties.getSources().get("default"));

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator.validate(properties));

        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(expectedProperty));
    }

    private void assertPoolValidationFailure(String expectedProperty,
                                             Consumer<SimpleRedisRouteProperties.PoolConfig> customizer) {
        SimpleRedisRouteProperties properties = baseProperties();
        SimpleRedisRouteProperties.DataSourceConfig config = properties.getSources().get("default");
        config.setHost("redis-internal.example.test");
        config.setPort(16379);
        config.setUsername("route-user");
        config.setPassword("opaque-credential-content");
        config.setClientName("route-client");
        enablePool(config);
        customizer.accept(config.getLettuce().getPool());

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator.validate(properties));

        assertEquals(ErrorCode.REDIS_ROUTE_005, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(expectedProperty));
        assertFalse(exception.getMessage().contains("redis-internal.example.test"));
        assertFalse(exception.getMessage().contains("16379"));
        assertFalse(exception.getMessage().contains("route-user"));
        assertFalse(exception.getMessage().contains("opaque-credential-content"));
        assertFalse(exception.getMessage().contains("route-client"));
    }

    private SimpleRedisRouteProperties baseProperties() {
        SimpleRedisRouteProperties properties = new SimpleRedisRouteProperties();
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        properties.getSources().put("default", config);
        return properties;
    }

    private SimpleRedisRouteProperties.RouteRule rule(String pattern, String type, String datasource, int priority) {
        SimpleRedisRouteProperties.RouteRule rule = new SimpleRedisRouteProperties.RouteRule();
        rule.setPattern(pattern);
        rule.setType(type);
        rule.setDatasource(datasource);
        rule.setPriority(priority);
        return rule;
    }
}
