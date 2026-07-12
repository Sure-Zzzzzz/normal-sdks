package io.github.surezzzzzz.sdk.lock.redis.executor;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 分布式锁 Lua 脚本常量，供所有 executor 共用。
 *
 * @author surezzzzzz
 */
public final class RedisLockScripts {

    /**
     * 原子解锁脚本：只有 value 匹配时才删除 key，避免误删其他持有者的锁。
     */
    public static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end",
            Long.class
    );

    private RedisLockScripts() {
        throw new UnsupportedOperationException("Utility class");
    }
}
