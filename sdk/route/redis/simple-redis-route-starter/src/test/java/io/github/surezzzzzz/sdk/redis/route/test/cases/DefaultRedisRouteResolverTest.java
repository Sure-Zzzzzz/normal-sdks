package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.resolver.DefaultRedisRouteResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 默认 Redis 路由解析器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultRedisRouteResolverTest {

    @Test
    public void testPriorityAndDeclarationOrder() {
        SimpleRedisRouteProperties properties = baseProperties();
        properties.getRules().add(rule("cache:user:", "prefix", "user-cache", 1));
        properties.getRules().add(rule("cache:", "prefix", "default", 1));
        properties.getRules().add(rule("lock:", "prefix", "lock", 0));

        DefaultRedisRouteResolver resolver = new DefaultRedisRouteResolver(properties, new RedisRoutePatternMatcher());

        assertEquals("lock", resolver.resolveDataSource("lock:order:001"));
        assertEquals("user-cache", resolver.resolveDataSource("cache:user:001"));
        assertEquals("default", resolver.resolveDataSource("cache:order:001"));
        assertEquals("default", resolver.resolveDataSource("unknown:001"));
    }

    @Test
    public void testBlankRouteKeyThrowsRouteException() {
        DefaultRedisRouteResolver resolver = new DefaultRedisRouteResolver(baseProperties(), new RedisRoutePatternMatcher());
        RouteException exception = assertThrows(RouteException.class, () -> resolver.resolveDataSource(" "));
        assertEquals(ErrorCode.REDIS_ROUTE_008, exception.getErrorCode());
    }

    private SimpleRedisRouteProperties baseProperties() {
        SimpleRedisRouteProperties properties = new SimpleRedisRouteProperties();
        properties.getSources().put("default", new SimpleRedisRouteProperties.DataSourceConfig());
        properties.getSources().put("user-cache", new SimpleRedisRouteProperties.DataSourceConfig());
        properties.getSources().put("lock", new SimpleRedisRouteProperties.DataSourceConfig());
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
