package io.github.surezzzzzz.sdk.cache.exception;

/**
 * 缓存预热异常
 *
 * @author surezzzzzz
 */
public class CacheWarmUpException extends SmartCacheException {

    private static final long serialVersionUID = 1L;

    public CacheWarmUpException(String errorCode, String message) {
        super(errorCode, message);
    }

    public CacheWarmUpException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
