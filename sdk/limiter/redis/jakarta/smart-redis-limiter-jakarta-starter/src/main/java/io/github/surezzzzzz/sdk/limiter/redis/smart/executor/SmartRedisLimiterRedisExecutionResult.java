package io.github.surezzzzzz.sdk.limiter.redis.smart.executor;

import lombok.Builder;
import lombok.Getter;

/**
 * SmartRedisLimiter Redis 执行结果
 *
 * @param <T> Redis 回调返回值类型
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterRedisExecutionResult<T> {

    /**
     * Redis 回调返回值
     */
    private final T value;

    /**
     * 路由 Key
     */
    private final String routeKey;

    /**
     * Redis datasource key
     */
    private final String datasourceKey;

    /**
     * Redis 模式
     */
    private final String redisMode;

    /**
     * 是否要求通过 redis-route 执行
     */
    private final boolean routeRequired;

    /**
     * 是否成功解析到 datasource
     */
    private final boolean routeResolved;
}
