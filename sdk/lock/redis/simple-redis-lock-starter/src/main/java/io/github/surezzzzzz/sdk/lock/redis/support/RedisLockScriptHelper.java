package io.github.surezzzzzz.sdk.lock.redis.support;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 分布式锁脚本 Helper
 *
 * @author surezzzzzz
 */
public final class RedisLockScriptHelper {

    public static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end",
            Long.class
    );

    private RedisLockScriptHelper() {
        throw new UnsupportedOperationException("Utility class");
    }
}
