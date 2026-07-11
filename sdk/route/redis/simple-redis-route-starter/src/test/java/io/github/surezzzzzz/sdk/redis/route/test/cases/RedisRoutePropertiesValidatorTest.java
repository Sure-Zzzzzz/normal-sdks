package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.RedisSourceMode;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.validator.RedisRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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
    public void testDatasourceConfigToStringDoesNotLeakPassword() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setPassword("secret-password");
        String text = config.toString();
        assertFalse(text.contains("secret-password"));
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
