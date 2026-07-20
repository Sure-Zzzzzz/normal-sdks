package io.github.surezzzzzz.sdk.cache.exception;

/**
 * 缓存配置异常
 *
 * @author surezzzzzz
 */
public class CacheConfigurationException extends SmartCacheException {

    private static final long serialVersionUID = 1L;

    public CacheConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public CacheConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
