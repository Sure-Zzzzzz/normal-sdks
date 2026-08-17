package io.github.surezzzzzz.sdk.redis.route.exception;

import lombok.Getter;

/**
 * Simple Redis Route 异常基类
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleRedisRouteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    /**
     * 创建不带原因异常的 Route 异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误说明
     */
    public SimpleRedisRouteException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建带原因异常的 Route 异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误说明
     * @param cause     原始异常
     */
    public SimpleRedisRouteException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
