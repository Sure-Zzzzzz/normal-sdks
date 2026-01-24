package io.github.surezzzzzz.sdk.auth.aksk.client.core.exception;

import lombok.Getter;

/**
 * Simple AKSK Client Core Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleAkskClientCoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleAkskClientCoreException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleAkskClientCoreException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
