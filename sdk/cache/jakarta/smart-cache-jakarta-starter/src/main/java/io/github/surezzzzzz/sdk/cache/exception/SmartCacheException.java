package io.github.surezzzzzz.sdk.cache.exception;

import lombok.Getter;

/**
 * Smart Cache 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class SmartCacheException extends RuntimeException {

    private static final long serialVersionUID = 2L;

    /**
     * 错误码
     */
    private final String errorCode;

    public SmartCacheException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SmartCacheException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
