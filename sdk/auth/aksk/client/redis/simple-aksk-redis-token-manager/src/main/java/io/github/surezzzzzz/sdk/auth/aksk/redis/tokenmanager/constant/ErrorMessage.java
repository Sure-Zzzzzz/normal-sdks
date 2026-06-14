package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 缓存 Key 错误 ====================

    /**
     * 缓存 Key 哈希算法不可用，模板参数: algorithm
     */
    public static final String CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE = "缓存 Key 哈希算法不可用: %s";
}
