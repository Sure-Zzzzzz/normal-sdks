package io.github.surezzzzzz.sdk.redis.route.resolver;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;

/**
 * Redis 路由解析器
 *
 * @author surezzzzzz
 */
public interface RedisRouteResolver {

    String resolveDataSource(String routeKey);

    SimpleRedisRouteProperties.RouteRule resolveRule(String routeKey);
}
