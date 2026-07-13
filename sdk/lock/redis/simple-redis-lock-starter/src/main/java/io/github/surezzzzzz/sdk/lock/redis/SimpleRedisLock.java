package io.github.surezzzzzz.sdk.lock.redis;

import io.github.surezzzzzz.sdk.lock.redis.annotation.SimpleRedisLockComponent;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁，支持单 Redis 与 route 两种执行模式。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleRedisLockComponent
@RequiredArgsConstructor
public class SimpleRedisLock {

    private final RedisLockExecutor redisLockExecutor;

    /**
     * 尝试加锁。
     *
     * @param lockKey    锁 key
     * @param lockValue  锁 value（持有者标识，建议使用唯一 ID）
     * @param expireTime 过期时间
     * @param timeUnit   过期时间单位
     * @return true 表示加锁成功
     */
    public boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit) {
        log.debug("尝试加锁，lockKey={}", lockKey);
        boolean success = redisLockExecutor.tryLock(lockKey, lockValue, expireTime, timeUnit);
        if (success) {
            log.info("加锁成功，lockKey={}, expireTime={}{}", lockKey, expireTime, timeUnit);
        } else {
            log.info("加锁失败，lockKey={} 已被持有", lockKey);
        }
        return success;
    }

    /**
     * 释放锁。只有 lockValue 与当前持有者匹配时才删除 key。
     * Redis 命令执行失败时抛出异常，不吞掉。
     *
     * @param lockKey   锁 key
     * @param lockValue 锁 value（持有者标识）
     * @return true 表示成功释放，false 表示 value 不匹配或锁已过期
     */
    public boolean unlock(String lockKey, String lockValue) {
        log.debug("尝试解锁，lockKey={}", lockKey);
        boolean released = redisLockExecutor.unlock(lockKey, lockValue);
        if (released) {
            log.info("解锁成功，lockKey={}", lockKey);
        } else {
            log.info("解锁未生效，lockKey={} 持有者不匹配或锁已过期", lockKey);
        }
        return released;
    }
}
