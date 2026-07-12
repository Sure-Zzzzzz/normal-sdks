package io.github.surezzzzzz.sdk.lock.redis.executor;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁执行器接口，屏蔽单 Redis 与 route 模式差异。
 *
 * @author surezzzzzz
 */
public interface RedisLockExecutor {

    /**
     * 尝试加锁。
     *
     * @param lockKey    锁 key
     * @param lockValue  锁 value（持有者标识）
     * @param expireTime 过期时间
     * @param timeUnit   过期时间单位
     * @return true 表示加锁成功，false 表示锁已被持有
     */
    boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit);

    /**
     * 释放锁。只有 lockValue 与当前持有者匹配时才删除 key。
     *
     * @param lockKey   锁 key
     * @param lockValue 锁 value（持有者标识）
     * @return true 表示成功释放，false 表示 value 不匹配或锁已过期
     */
    boolean unlock(String lockKey, String lockValue);
}
