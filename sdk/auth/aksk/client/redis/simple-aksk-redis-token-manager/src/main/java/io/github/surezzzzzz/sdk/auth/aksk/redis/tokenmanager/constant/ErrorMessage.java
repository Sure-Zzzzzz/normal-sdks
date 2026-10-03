package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * Token 缓存处理失败
     */
    public static final String TOKEN_CACHE_PROCESSING_FAILED = "Token 缓存处理失败";

    /**
     * 缓存 Key 哈希算法不可用，模板参数: algorithm
     */
    public static final String CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE = "缓存 Key 哈希算法不可用: %s";

    // ==================== 缓存 Key 错误 ====================

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
