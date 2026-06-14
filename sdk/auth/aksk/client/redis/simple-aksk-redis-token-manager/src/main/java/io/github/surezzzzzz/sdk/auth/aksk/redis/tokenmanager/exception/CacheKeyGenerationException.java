package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.exception;

/**
 * Cache Key Generation Exception
 *
 * <p>缓存 Key 生成失败时抛出（如哈希算法不可用）。
 *
 * @author surezzzzzz
 */
public class CacheKeyGenerationException extends SimpleAkskRedisTokenManagerException {

    private static final long serialVersionUID = 1L;

    public CacheKeyGenerationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public CacheKeyGenerationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
