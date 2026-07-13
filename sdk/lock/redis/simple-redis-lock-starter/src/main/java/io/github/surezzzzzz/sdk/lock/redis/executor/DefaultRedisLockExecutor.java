package io.github.surezzzzzz.sdk.lock.redis.executor;

import io.github.surezzzzzz.sdk.lock.redis.support.RedisLockScriptHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 默认单 Redis 锁执行器，使用 simpleRedisLockRedisTemplate。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultRedisLockExecutor implements RedisLockExecutor {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireTime, timeUnit);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public boolean unlock(String lockKey, String lockValue) {
        Long result = redisTemplate.execute(
                RedisLockScriptHelper.UNLOCK_SCRIPT,
                Collections.singletonList(lockKey),
                lockValue
        );
        return Long.valueOf(1).equals(result);
    }
}
