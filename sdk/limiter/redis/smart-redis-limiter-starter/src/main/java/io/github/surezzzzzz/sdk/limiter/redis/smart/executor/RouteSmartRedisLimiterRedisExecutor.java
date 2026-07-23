package io.github.surezzzzzz.sdk.limiter.redis.smart.executor;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterRedisException;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 基于 Redis Route 的 Redis 执行器
 *
 * @author surezzzzzz
 */
@Slf4j
public class RouteSmartRedisLimiterRedisExecutor implements SmartRedisLimiterRedisExecutor {

    private final RedisRouteTemplate redisRouteTemplate;
    private final SmartRedisLimiterProperties properties;
    private final Set<String> hashTagWarningDatasourceKeys = ConcurrentHashMap.newKeySet();

    public RouteSmartRedisLimiterRedisExecutor(RedisRouteTemplate redisRouteTemplate) {
        this(redisRouteTemplate, new SmartRedisLimiterProperties());
    }

    public RouteSmartRedisLimiterRedisExecutor(RedisRouteTemplate redisRouteTemplate,
                                               SmartRedisLimiterProperties properties) {
        this.redisRouteTemplate = redisRouteTemplate;
        this.properties = properties;
    }

    @Override
    public <T> SmartRedisLimiterRedisExecutionResult<T> execute(String routeKey,
                                                                Function<StringRedisTemplate, T> callback) {
        RouteSnapshot routeSnapshot = buildRouteSnapshot(routeKey);
        warnIfClusterHashTagDisabled(routeSnapshot);
        try {
            T value = redisRouteTemplate.execute(routeKey, redisTemplate -> {
                try {
                    return callback.apply(redisTemplate);
                } catch (RuntimeException e) {
                    throw new RedisExecutionExceptionWrapper(e);
                }
            });
            return SmartRedisLimiterRedisExecutionResult.<T>builder()
                    .value(value)
                    .routeKey(routeSnapshot.routeKey)
                    .datasourceKey(routeSnapshot.datasourceKey)
                    .redisMode(routeSnapshot.redisMode)
                    .routeRequired(routeSnapshot.routeRequired)
                    .routeResolved(routeSnapshot.routeResolved)
                    .build();
        } catch (RedisExecutionExceptionWrapper e) {
            throw redisException(routeSnapshot, e.getCause(),
                    SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR);
        } catch (RouteException e) {
            throw redisException(routeSnapshot, e,
                    SmartRedisLimiterConstant.FALLBACK_REASON_ROUTE_ERROR);
        } catch (RuntimeException e) {
            throw redisException(routeSnapshot, e,
                    SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR);
        }
    }

    private SmartRedisLimiterRedisException redisException(RouteSnapshot routeSnapshot,
                                                           Throwable cause,
                                                           String fallbackReason) {
        return new SmartRedisLimiterRedisException(
                ErrorCode.ROUTE_EXECUTION_FAILED,
                String.format(ErrorMessage.ROUTE_EXECUTION_FAILED, cause.getMessage()),
                cause,
                routeSnapshot.routeKey,
                routeSnapshot.datasourceKey,
                routeSnapshot.redisMode,
                routeSnapshot.routeRequired,
                routeSnapshot.routeResolved,
                fallbackReason);
    }

    private RouteSnapshot buildRouteSnapshot(String routeKey) {
        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.routeKey = routeKey;
        snapshot.routeRequired = true;
        snapshot.redisMode = SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN;
        try {
            RedisServerInfo serverInfo = redisRouteTemplate.serverInfoByKey(routeKey);
            if (serverInfo == null || serverInfo.getDatasourceKey() == null
                    || serverInfo.getDatasourceKey().trim().isEmpty()) {
                return snapshot;
            }
            snapshot.datasourceKey = serverInfo.getDatasourceKey();
            snapshot.routeResolved = true;
            snapshot.redisMode = normalizeRedisMode(serverInfo.getRedisMode());
            return snapshot;
        } catch (RuntimeException e) {
            log.debug("SmartRedisLimiter 获取 Redis Route 观测信息失败: routeKey={}", routeKey, e);
            return snapshot;
        }
    }

    private void warnIfClusterHashTagDisabled(RouteSnapshot routeSnapshot) {
        if (Boolean.TRUE.equals(properties.getRedis().getUseHashTag())
                || !SmartRedisLimiterConstant.REDIS_MODE_CLUSTER.equals(routeSnapshot.redisMode)
                || routeSnapshot.datasourceKey == null
                || !hashTagWarningDatasourceKeys.add(routeSnapshot.datasourceKey)) {
            return;
        }
        log.warn("SmartRedisLimiter 已关闭 Hash Tag 且路由到 Cluster 数据源，"
                        + "多窗口 Lua 可能触发 CROSSSLOT: datasourceKey={}",
                routeSnapshot.datasourceKey);
    }

    private String normalizeRedisMode(String redisMode) {
        if (SmartRedisLimiterConstant.REDIS_MODE_CLUSTER.equalsIgnoreCase(redisMode)) {
            return SmartRedisLimiterConstant.REDIS_MODE_CLUSTER;
        }
        if (SmartRedisLimiterConstant.REDIS_MODE_STANDALONE.equalsIgnoreCase(redisMode)) {
            return SmartRedisLimiterConstant.REDIS_MODE_STANDALONE;
        }
        return SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN;
    }

    private static class RouteSnapshot {
        private String routeKey;
        private String datasourceKey;
        private String redisMode;
        private boolean routeRequired;
        private boolean routeResolved;
    }

    /**
     * 标记 callback 内部 Redis 执行异常，避免和 callback 之前的路由异常混淆。
     */
    private static class RedisExecutionExceptionWrapper extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private RedisExecutionExceptionWrapper(Throwable cause) {
            super(cause);
        }
    }
}
