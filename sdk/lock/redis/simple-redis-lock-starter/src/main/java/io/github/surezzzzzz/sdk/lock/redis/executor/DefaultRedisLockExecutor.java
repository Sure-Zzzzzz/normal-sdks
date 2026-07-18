package io.github.surezzzzzz.sdk.lock.redis.executor;

import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
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
        return SimpleRedisLockConstant.REDIS_SCRIPT_SUCCESS_RESULT.equals(result);
    }

    @Override
    public boolean renew(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
        Long result = redisTemplate.execute(
                RedisLockScriptHelper.RENEW_SCRIPT,
                Collections.singletonList(lockKey),
                lockValue,
                String.valueOf(timeUnit.toMillis(leaseTime))
        );
        return SimpleRedisLockConstant.REDIS_SCRIPT_SUCCESS_RESULT.equals(result);
    }
}
