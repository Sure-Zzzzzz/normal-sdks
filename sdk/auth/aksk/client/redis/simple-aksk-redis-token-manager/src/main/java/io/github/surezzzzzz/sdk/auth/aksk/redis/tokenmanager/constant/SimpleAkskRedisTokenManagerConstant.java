package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant;

/**
 * Simple AKSK Redis Token Manager Constants
 *
 * @author surezzzzzz
 */
public final class SimpleAkskRedisTokenManagerConstant {

    private SimpleAkskRedisTokenManagerConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 缓存 Key 哈希相关 ====================

    /**
     * 缓存 Key 哈希算法
     */
    public static final String CACHE_KEY_HASH_ALGORITHM = "SHA-256";

    /**
     * 哈希截断字节数（128-bit，碰撞概率 ≈ 1/2^64）
     */
    public static final int CACHE_KEY_HASH_TRUNCATE_BYTES = 16;

    /**
     * null/空 securityContext 的固定 Key
     */
    public static final String DEFAULT_CACHE_KEY = "default";

    // ==================== Token 缓存相关 ====================

    /**
     * 默认 Token 缓存名称（SmartCache cacheName）
     */
    public static final String DEFAULT_TOKEN_CACHE_NAME = "aksk-client-token";

    // ==================== 锁与轮询参数 ====================

    /**
     * L2 轮询间隔（毫秒）
     */
    public static final int L2_POLL_INTERVAL_MS = 500;

    /**
     * 分布式锁超时 fallback 值（秒），当 SmartCacheProperties.getLock() 为 null 时使用
     */
    public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 30;
}
