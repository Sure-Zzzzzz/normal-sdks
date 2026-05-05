package io.github.surezzzzzz.sdk.cache.exception;

import lombok.Getter;

/**
 * Smart Cache Exception
 * <p>
 * 缓存基础异常
 * </p>
 *
 * @author surezzzzzz
 */
@Getter
public class SmartCacheException extends RuntimeException {

    private static final long serialVersionUID = 1L;

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
