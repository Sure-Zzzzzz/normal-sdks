package io.github.surezzzzzz.sdk.lock.redis;

import io.github.surezzzzzz.sdk.lock.redis.configuration.LockComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/12/11 11:23
 */
@LockComponent
@Slf4j
public class SimpleRedisLock {
    @Autowired
    @Qualifier("simpleRedisLockRedisTemplate")
    protected RedisTemplate<String, String> simpleRedisLockRedisTemplate;

    // Lua脚本：原子性地检查并删除锁
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    // 尝试获取锁
    public boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit) {
        log.debug("开始尝试加锁，lockKey:{}", lockKey);
        ValueOperations<String, String> ops = simpleRedisLockRedisTemplate.opsForValue();
        Boolean success = ops.setIfAbsent(lockKey, lockValue, expireTime, timeUnit);
        if (success != null && success) {
            log.info("成功获取锁，lockKey={}, lockValue={}, expireTime={}{}", lockKey, lockValue, expireTime, timeUnit);
            return true;
        } else {
            log.info("获取锁失败，lockKey={} 已存在", lockKey);
            return false;
        }
    }

    // 释放锁
    public void unlock(String lockKey, String lockValue) {
        log.debug("开始尝试解锁，lockKey:{}", lockKey);
        try {
            Long result = simpleRedisLockRedisTemplate.execute(
                    UNLOCK_REDIS_SCRIPT,
                    Collections.singletonList(lockKey),
                    lockValue
            );
            if (result != null && result == 1) {
                log.info("成功释放锁，lockKey={}, lockValue={}", lockKey, lockValue);
            } else {
                log.info("释放锁失败，lockKey={} 的持有者与当前持有者不匹配或锁已过期", lockKey);
            }
        } catch (Exception e) {
            log.error("释放锁异常，lockKey={}, lockValue={}", lockKey, lockValue, e);
        }
    }
}