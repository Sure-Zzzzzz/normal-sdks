package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.exception;

import lombok.Getter;

/**
 * Simple AKSK Redis Token Manager Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleAkskRedisTokenManagerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleAkskRedisTokenManagerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleAkskRedisTokenManagerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
