package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 缓存 Key 错误 ====================

    /**
     * 缓存 Key 哈希算法不可用
     */
    public static final String CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE = "CACHE_KEY_001";
}
