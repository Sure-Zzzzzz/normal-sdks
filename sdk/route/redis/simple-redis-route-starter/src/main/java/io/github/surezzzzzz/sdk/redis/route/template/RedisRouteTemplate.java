package io.github.surezzzzzz.sdk.redis.route.template;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
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

    public StringRedisTemplate stringTemplate() {
        return registry.getStringRedisTemplate();
    }

    public StringRedisTemplate stringTemplate(String datasourceKey) {
        return registry.getStringRedisTemplate(validateDatasourceKey(datasourceKey));
    }

    public StringRedisTemplate stringTemplateByKey(String redisKey) {
        return registry.getStringRedisTemplate(routeResolver.resolveDataSource(validateRouteKey(redisKey)));
    }

    public RedisConnectionFactory connectionFactory() {
        return registry.getConnectionFactory();
    }

    public RedisConnectionFactory connectionFactory(String datasourceKey) {
        return registry.getConnectionFactory(validateDatasourceKey(datasourceKey));
    }

    public RedisConnectionFactory connectionFactoryByKey(String redisKey) {
        return registry.getConnectionFactory(routeResolver.resolveDataSource(validateRouteKey(redisKey)));
    }

    public <T> T execute(String redisKey, Function<StringRedisTemplate, T> callback) {
        validateCallback(callback);
        return callback.apply(stringTemplateByKey(redisKey));
    }

    public <T> T execute(Collection<String> redisKeys, Function<StringRedisTemplate, T> callback) {
        validateCallback(callback);
        String datasourceKey = resolveSameDatasource(redisKeys);
        return callback.apply(registry.getStringRedisTemplate(datasourceKey));
    }

    public <T> T executeOn(String datasourceKey, Function<StringRedisTemplate, T> callback) {
        validateCallback(callback);
        return callback.apply(registry.getStringRedisTemplate(validateDatasourceKey(datasourceKey)));
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
