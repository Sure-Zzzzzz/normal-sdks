package io.github.surezzzzzz.sdk.cache.exception;

/**
 * 缓存序列化异常
 *
 * @author surezzzzzz
 */
public class CacheSerializationException extends SmartCacheException {

    private static final long serialVersionUID = 1L;

    public CacheSerializationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public CacheSerializationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
