package io.github.surezzzzzz.sdk.cache.exception;

/**
 * Cache Load Exception
 * <p>
 * 缓存加载异常
 * </p>
 *
 * @author Sure
 */
public class CacheLoadException extends SmartCacheException {

    public CacheLoadException(String message) {
        super(message);
    }

    public CacheLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public CacheLoadException(Throwable cause) {
        super(cause);
    }
}
