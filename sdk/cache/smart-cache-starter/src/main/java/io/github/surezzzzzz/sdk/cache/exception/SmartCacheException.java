package io.github.surezzzzzz.sdk.cache.exception;

/**
 * Smart Cache Exception
 * <p>
 * 缓存基础异常
 * </p>
 *
 * @author Sure
 */
public class SmartCacheException extends RuntimeException {

    public SmartCacheException(String message) {
        super(message);
    }

    public SmartCacheException(String message, Throwable cause) {
        super(message, cause);
    }

    public SmartCacheException(Throwable cause) {
        super(cause);
    }
}
