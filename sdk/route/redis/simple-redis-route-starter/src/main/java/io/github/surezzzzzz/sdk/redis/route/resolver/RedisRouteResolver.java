package io.github.surezzzzzz.sdk.redis.route.resolver;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;

/**
 * Redis 路由解析器
 *
 * @author surezzzzzz
 */
public interface RedisRouteResolver {

    /**
     * 按路由 key 解析目标数据源。
     *
     * @param routeKey 路由 key
     * @return 匹配规则的数据源；未匹配时返回 default-source
     */
    String resolveDataSource(String routeKey);

    /**
     * 按路由 key 查找首条匹配且已启用的规则。
     *
     * @param routeKey 路由 key
     * @return 匹配的规则；未匹配时返回 null
     */
    SimpleRedisRouteProperties.RouteRule resolveRule(String routeKey);
}
