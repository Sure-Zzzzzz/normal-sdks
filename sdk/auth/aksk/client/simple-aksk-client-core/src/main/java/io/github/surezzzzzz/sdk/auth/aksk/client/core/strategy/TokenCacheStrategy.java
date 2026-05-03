package io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;

/**
 * Token Cache Strategy Interface
 *
 * <p>定义 Token 缓存的统一接口，使用 Strategy 模式支持不同的缓存实现（Redis、HttpSession 等）。
 *
 * @author surezzzzzz
 */
public interface TokenCacheStrategy {

    /**
     * 生成缓存 Key
     *
     * @param securityContext Security Context（JSON 字符串或 null）
     * @return 缓存 Key
     */
    String generateCacheKey(String securityContext);

    /**
     * 获取缓存的 Token
     *
     * @param cacheKey 缓存 Key
     * @return 缓存的 Token，如果不存在或已过期则返回 null
     */
    String get(String cacheKey);

    /**
     * 缓存 Token
     *
     * @param cacheKey         缓存 Key
     * @param token            Token 值
     * @param expiresInSeconds 服务端返回的过期时间（秒）
     */
    void put(String cacheKey, String token, long expiresInSeconds);

    /**
     * 清除缓存的 Token
     *
     * @param cacheKey 缓存 Key
     */
    void remove(String cacheKey);

    /**
     * 计算实际缓存 TTL（秒）
     *
     * <p>提前 {@link SimpleAkskClientCoreConstant#TOKEN_EARLY_EXPIRY_SECONDS} 秒过期，
     * 且不低于 {@link SimpleAkskClientCoreConstant#TOKEN_MIN_TTL_SECONDS}，避免边界情况。
     *
     * @param expiresInSeconds 服务端返回的过期时间（秒）
     * @return 实际缓存 TTL（秒）
     */
    default long calculateTtl(long expiresInSeconds) {
        return Math.max(
                expiresInSeconds - SimpleAkskClientCoreConstant.TOKEN_EARLY_EXPIRY_SECONDS,
                SimpleAkskClientCoreConstant.TOKEN_MIN_TTL_SECONDS
        );
    }
}
