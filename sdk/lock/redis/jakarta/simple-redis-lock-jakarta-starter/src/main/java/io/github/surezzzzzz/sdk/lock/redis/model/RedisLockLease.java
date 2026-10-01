package io.github.surezzzzzz.sdk.lock.redis.model;

import io.github.surezzzzzz.sdk.lock.redis.exception.ValidationException;

import java.util.concurrent.TimeUnit;

/**
 * Redis 锁租约句柄。
 *
 * @author surezzzzzz
 */
public interface RedisLockLease extends AutoCloseable {

    /**
     * 续租。
     *
     * @param leaseTime 租约时长
     * @param timeUnit  租约时间单位
     * @return true 表示续租成功，false 表示锁已失效、已被释放或 owner 已变更
     * @throws ValidationException 当前租约未释放且租约时长不足 1 毫秒或时间单位为空
     */
    boolean renew(long leaseTime, TimeUnit timeUnit);

    /**
     * 释放锁。
     *
     * @return true 表示成功释放，false 表示锁已失效、owner 已变更或已释放
     */
    boolean release();

    /**
     * 关闭租约句柄。
     */
    @Override
    void close();
}
