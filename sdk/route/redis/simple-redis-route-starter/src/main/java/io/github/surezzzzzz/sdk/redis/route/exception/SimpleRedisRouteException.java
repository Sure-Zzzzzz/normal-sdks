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

    public SimpleRedisRouteException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleRedisRouteException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
