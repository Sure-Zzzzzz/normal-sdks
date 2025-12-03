package io.github.surezzzzzz.sdk.lock.redis;

import io.github.surezzzzzz.sdk.lock.redis.configuration.LockComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
        ValueOperations<String, String> ops = simpleRedisLockRedisTemplate.opsForValue();
        String currentValue = ops.get(lockKey);
        // 如果当前值为 null，说明锁已经过期或自动释放
        if (currentValue == null) {
            log.info("锁已经过期或被自动释放，lockKey={} 已不存在", lockKey);
            return;
        }
        // 只有当前持有锁的客户端才能释放锁
        if (lockValue.equals(currentValue)) {
            simpleRedisLockRedisTemplate.delete(lockKey);
            log.info("成功释放锁，lockKey={}, lockValue={}", lockKey, lockValue);
        } else {
            log.info("释放锁失败，lockKey={} 的持有者与当前持有者不匹配，锁未被删除", lockKey);
        }
    }
}