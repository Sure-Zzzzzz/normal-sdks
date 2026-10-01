package io.github.surezzzzzz.sdk.lock.redis.support;

import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 分布式锁脚本 Helper
 *
 * @author surezzzzzz
 */
public final class RedisLockScriptHelper {

    public static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            SimpleRedisLockConstant.REDIS_UNLOCK_SCRIPT,
            Long.class
    );

    public static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            SimpleRedisLockConstant.REDIS_RENEW_SCRIPT,
            Long.class
    );

    private RedisLockScriptHelper() {
        throw new UnsupportedOperationException(SimpleRedisLockConstant.UTILITY_CLASS_ERROR_MESSAGE);
    }
}
