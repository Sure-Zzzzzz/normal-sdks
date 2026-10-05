package io.github.surezzzzzz.sdk.limiter.redis.smart.executor;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.function.Function;

/**
 * SmartRedisLimiter Redis 执行器
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterRedisExecutor {

    /**
     * 按 routeKey 执行 Redis 操作
     *
     * @param routeKey routeKey
     * @param callback Redis 操作回调
     * @param <T>      执行结果类型
     * @return 执行结果
     */
    <T> SmartRedisLimiterRedisExecutionResult<T> execute(String routeKey,
                                                         Function<StringRedisTemplate, T> callback);
}
