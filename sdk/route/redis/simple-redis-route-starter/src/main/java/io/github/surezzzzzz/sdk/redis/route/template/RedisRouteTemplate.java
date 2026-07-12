package io.github.surezzzzzz.sdk.redis.route.template;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.resolver.RedisRouteResolver;
import io.github.surezzzzzz.sdk.redis.route.support.RedisRouteStringHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Redis route 显式路由门面
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class RedisRouteTemplate {

    private final SimpleRedisRouteRegistry registry;
    private final RedisRouteResolver routeResolver;

    /**
     * 获取默认 datasource 的 StringRedisTemplate。
     *
     * @return 默认 datasource 的 StringRedisTemplate
     */
    public StringRedisTemplate stringTemplate() {
        return registry.getStringRedisTemplate();
    }

    /**
     * 获取指定 datasource 的 StringRedisTemplate。
     *
     * @param datasourceKey datasource key
     * @return 指定 datasource 的 StringRedisTemplate
     * @throws RouteException datasource key 为空或未注册时抛出
     */
    public StringRedisTemplate stringTemplate(String datasourceKey) {
        return registry.getStringRedisTemplate(validateDatasourceKey(datasourceKey));
    }

    /**
     * 按 Redis key 路由后获取 StringRedisTemplate。
     *
     * @param redisKey Redis key
     * @return 路由命中的 StringRedisTemplate
     * @throws RouteException Redis key 为空或路由目标 datasource 未注册时抛出
     */
    public StringRedisTemplate stringTemplateByKey(String redisKey) {
        return registry.getStringRedisTemplate(routeResolver.resolveDataSource(validateRouteKey(redisKey)));
    }

    /**
     * 获取默认 datasource 的连接工厂。
     *
     * @return 默认 datasource 的连接工厂
     */
    public RedisConnectionFactory connectionFactory() {
        return registry.getConnectionFactory();
    }

    /**
     * 获取指定 datasource 的连接工厂。
     *
     * @param datasourceKey datasource key
     * @return 指定 datasource 的连接工厂
     * @throws RouteException datasource key 为空或未注册时抛出
     */
    public RedisConnectionFactory connectionFactory(String datasourceKey) {
        return registry.getConnectionFactory(validateDatasourceKey(datasourceKey));
    }

    /**
     * 按 Redis key 路由后获取连接工厂。
     *
     * @param redisKey Redis key
     * @return 路由命中的连接工厂
     * @throws RouteException Redis key 为空或路由目标 datasource 未注册时抛出
     */
    public RedisConnectionFactory connectionFactoryByKey(String redisKey) {
        return registry.getConnectionFactory(routeResolver.resolveDataSource(validateRouteKey(redisKey)));
    }

    /**
     * 按 Redis key 路由后执行回调。
     *
     * @param redisKey Redis key
     * @param callback Redis 操作回调
     * @param <T>      回调返回值类型
     * @return 回调执行结果
     * @throws RouteException Redis key 为空、callback 为空或路由目标 datasource 未注册时抛出
     */
    public <T> T execute(String redisKey, Function<StringRedisTemplate, T> callback) {
        validateCallback(callback);
        return callback.apply(stringTemplateByKey(redisKey));
    }

    /**
     * 校验多个 Redis key 路由到同一 datasource 后执行回调。
     *
     * @param redisKeys Redis key 集合
     * @param callback  Redis 操作回调
     * @param <T>       回调返回值类型
     * @return 回调执行结果
     * @throws RouteException key 集合为空、callback 为空或多个 key 跨 datasource 时抛出
     */
    public <T> T execute(Collection<String> redisKeys, Function<StringRedisTemplate, T> callback) {
        validateCallback(callback);
        String datasourceKey = resolveSameDatasource(redisKeys);
        return callback.apply(registry.getStringRedisTemplate(datasourceKey));
    }

    /**
     * 在指定 datasource 上执行回调。
     *
     * @param datasourceKey datasource key
     * @param callback      Redis 操作回调
     * @param <T>           回调返回值类型
     * @return 回调执行结果
     * @throws RouteException datasource key 为空、未注册或 callback 为空时抛出
     */
    public <T> T executeOn(String datasourceKey, Function<StringRedisTemplate, T> callback) {
        validateCallback(callback);
        return callback.apply(registry.getStringRedisTemplate(validateDatasourceKey(datasourceKey)));
    }

    /**
     * 获取默认数据源的 Redis Server 信息快照。
     *
     * @return 默认数据源的 Redis Server 信息快照
     */
    public RedisServerInfo serverInfo() {
        return registry.getServerInfo(registry.getDefaultDatasourceKey());
    }

    /**
     * 按 datasource key 获取 Redis Server 信息快照。
     *
     * @param datasourceKey datasource key
     * @return 指定 datasource 的 Redis Server 信息快照
     * @throws RouteException datasource key 为空或未注册时抛出
     */
    public RedisServerInfo serverInfo(String datasourceKey) {
        return registry.getServerInfo(validateDatasourceKey(datasourceKey));
    }

    /**
     * 按 Redis key 路由后获取对应数据源的 Redis Server 信息快照。
     *
     * @param redisKey Redis key
     * @return 路由命中 datasource 的 Redis Server 信息快照
     * @throws RouteException Redis key 为空或路由目标 datasource 未注册时抛出
     */
    public RedisServerInfo serverInfoByKey(String redisKey) {
        String datasourceKey = routeResolver.resolveDataSource(validateRouteKey(redisKey));
        return registry.getServerInfo(datasourceKey);
    }

    private String resolveSameDatasource(Collection<String> redisKeys) {
        if (redisKeys == null || redisKeys.isEmpty()) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_008, ErrorMessage.ROUTE_KEY_EMPTY);
        }
        Set<String> datasources = new LinkedHashSet<>();
        for (String redisKey : redisKeys) {
            datasources.add(routeResolver.resolveDataSource(validateRouteKey(redisKey)));
        }
        if (datasources.size() != 1) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_009,
                    String.format(ErrorMessage.ROUTE_CROSS_DATASOURCE, datasources, redisKeys));
        }
        return datasources.iterator().next();
    }

    private String validateRouteKey(String redisKey) {
        if (!RedisRouteStringHelper.hasText(redisKey)) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_008, ErrorMessage.ROUTE_KEY_EMPTY);
        }
        return redisKey;
    }

    private String validateDatasourceKey(String datasourceKey) {
        if (!RedisRouteStringHelper.hasText(datasourceKey)) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_003,
                    String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, registry.getDatasourceKeys()));
        }
        return datasourceKey;
    }

    private void validateCallback(Function<StringRedisTemplate, ?> callback) {
        if (callback == null) {
            throw new RouteException(ErrorCode.REDIS_ROUTE_010, ErrorMessage.CALLBACK_EMPTY);
        }
    }
}
