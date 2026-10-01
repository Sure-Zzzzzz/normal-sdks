package io.github.surezzzzzz.sdk.lock.redis;

import io.github.surezzzzzz.sdk.lock.redis.annotation.SimpleRedisLockComponent;
import io.github.surezzzzzz.sdk.lock.redis.constant.ErrorCode;
import io.github.surezzzzzz.sdk.lock.redis.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
import io.github.surezzzzzz.sdk.lock.redis.exception.ValidationException;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static void validateLeaseTime(long leaseTime, TimeUnit timeUnit) {
        if (timeUnit == null) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_LEASE_TIME_UNIT_REQUIRED,
                    ErrorMessage.LEASE_TIME_UNIT_REQUIRED
            );
        }
        if (timeUnit.toMillis(leaseTime) < SimpleRedisLockConstant.MIN_LEASE_MILLIS) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND,
                    ErrorMessage.LEASE_TIME_MUST_BE_AT_LEAST_ONE_MILLISECOND
            );
        }
    }

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
        log.debug("开始执行 Redis 分布式锁加锁");
        boolean success = redisLockExecutor.tryLock(lockKey, lockValue, expireTime, timeUnit);
        log.debug("Redis 分布式锁加锁完成，成功={}", success);
        return success;
    }

    /**
     * 尝试获取显式续租的锁租约。
     *
     * @param lockKey   锁 key
     * @param leaseTime 租约时长
     * @param timeUnit  租约时间单位
     * @return 获取成功时返回租约句柄，锁已被持有时返回空
     * @throws ValidationException 租约时长不足 1 毫秒或时间单位为空
     */
    public Optional<RedisLockLease> tryLockWithLease(String lockKey, long leaseTime, TimeUnit timeUnit) {
        validateLeaseTime(leaseTime, timeUnit);
        String lockValue = UUID.randomUUID().toString();
        log.debug("开始获取 Redis 分布式锁租约");
        if (!redisLockExecutor.tryLock(lockKey, lockValue, leaseTime, timeUnit)) {
            log.debug("Redis 分布式锁租约获取完成，成功=false");
            return Optional.empty();
        }
        log.debug("Redis 分布式锁租约获取完成，成功=true");
        return Optional.of(new Lease(lockKey, lockValue, redisLockExecutor));
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
        log.debug("开始执行 Redis 分布式锁解锁");
        boolean released = redisLockExecutor.unlock(lockKey, lockValue);
        log.debug("Redis 分布式锁解锁完成，成功={}", released);
        return released;
    }

    private static final class Lease implements RedisLockLease {

        private final String lockKey;
        private final String lockValue;
        private final RedisLockExecutor redisLockExecutor;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Lease(String lockKey, String lockValue, RedisLockExecutor redisLockExecutor) {
            this.lockKey = lockKey;
            this.lockValue = lockValue;
            this.redisLockExecutor = redisLockExecutor;
        }

        @Override
        public synchronized boolean renew(long leaseTime, TimeUnit timeUnit) {
            if (released.get()) {
                return false;
            }
            validateLeaseTime(leaseTime, timeUnit);
            return redisLockExecutor.renew(lockKey, lockValue, leaseTime, timeUnit);
        }

        @Override
        public synchronized boolean release() {
            if (released.get()) {
                return false;
            }
            boolean unlocked = redisLockExecutor.unlock(lockKey, lockValue);
            released.set(true);
            return unlocked;
        }

        @Override
        public void close() {
            release();
        }
    }
}
