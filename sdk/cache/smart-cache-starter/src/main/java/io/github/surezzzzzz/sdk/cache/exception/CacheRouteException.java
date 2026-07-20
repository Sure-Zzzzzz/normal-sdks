package io.github.surezzzzzz.sdk.cache.exception;

/**
 * 缓存路由异常
 *
 * @author surezzzzzz
 */
public class CacheRouteException extends SmartCacheException {

    private static final long serialVersionUID = 1L;

    public CacheRouteException(String errorCode, String message) {
        super(errorCode, message);
    }

    public CacheRouteException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
