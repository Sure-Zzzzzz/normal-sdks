package io.github.surezzzzzz.sdk.cache.exception;

/**
 * 缓存加载异常
 *
 * @author surezzzzzz
 */
public class CacheLoadException extends SmartCacheException {

    private static final long serialVersionUID = 2L;

    public CacheLoadException(String errorCode, String message) {
        super(errorCode, message);
    }

    public CacheLoadException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
