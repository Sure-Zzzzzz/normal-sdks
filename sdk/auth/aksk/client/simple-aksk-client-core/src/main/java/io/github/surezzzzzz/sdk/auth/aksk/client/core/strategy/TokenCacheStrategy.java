package io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy;

/**
 * Token Cache Strategy Interface
 * <p>
 * 定义 Token 缓存的统一接口
 *
 * @author surezzzzzz
 */
public interface TokenCacheStrategy {

    /**
     * 生成缓存 Key
     * <p>
     * 不同的策略可能使用不同的 Key 格式
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
     * @param expiresInSeconds 过期时间（秒）
     */
    void put(String cacheKey, String token, long expiresInSeconds);

    /**
     * 清除缓存的 Token
     *
     * @param cacheKey 缓存 Key
     */
    void remove(String cacheKey);
}
