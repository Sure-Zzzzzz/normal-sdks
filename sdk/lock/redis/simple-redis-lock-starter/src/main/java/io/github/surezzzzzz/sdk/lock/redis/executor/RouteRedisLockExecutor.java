package io.github.surezzzzzz.sdk.lock.redis.executor;

import io.github.surezzzzzz.sdk.lock.redis.support.RedisLockScriptHelper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Route 模式锁执行器，按 lockKey 通过 RedisRouteTemplate 路由到对应 datasource。
 * 加锁和解锁均使用同一 lockKey 路由，保证同一把锁始终落在同一个 datasource。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class RouteRedisLockExecutor implements RedisLockExecutor {

    private final RedisRouteTemplate redisRouteTemplate;

    @Override
    public boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit) {
        Boolean success = redisRouteTemplate.execute(lockKey, template ->
                template.opsForValue().setIfAbsent(lockKey, lockValue, expireTime, timeUnit)
        );
        return Boolean.TRUE.equals(success);
    }

    @Override
    public boolean unlock(String lockKey, String lockValue) {
        Long result = redisRouteTemplate.execute(lockKey, template ->
                template.execute(
                        RedisLockScriptHelper.UNLOCK_SCRIPT,
                        Collections.singletonList(lockKey),
                        lockValue
                )
        );
        return Long.valueOf(1).equals(result);
    }
}
