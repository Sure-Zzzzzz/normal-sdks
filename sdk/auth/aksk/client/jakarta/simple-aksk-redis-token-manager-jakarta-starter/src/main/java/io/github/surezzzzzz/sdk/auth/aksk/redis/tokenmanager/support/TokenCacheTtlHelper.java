package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support;

import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant;

/**
 * Token 缓存 TTL 计算辅助类。
 *
 * <p>缓存不得晚于 Token 的真实失效时间；正常有效期额外预留提前失效窗口，避免调用方在边界时刻取到
 * 已失效或即将失效的 Token。
 *
 * @author surezzzzzz
 */
public final class TokenCacheTtlHelper {

    private TokenCacheTtlHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 根据真实失效时刻计算安全缓存 TTL。
     *
     * @param expiresAtSeconds Token 的真实失效 Unix 时间戳（秒）
     * @param nowSeconds       当前 Unix 时间戳（秒）
     * @return 可缓存秒数；0 表示 Token 已失效
     */
    public static int calculate(long expiresAtSeconds, long nowSeconds) {
        long remainingSeconds = expiresAtSeconds - nowSeconds;
        if (remainingSeconds <= 0) {
            return 0;
        }
        long ttlSeconds = remainingSeconds - SimpleAkskRedisTokenManagerConstant.TOKEN_CACHE_EXPIRY_BUFFER_SECONDS;
        return (int) Math.max(ttlSeconds, 1);
    }
}
