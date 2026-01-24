package io.github.surezzzzzz.sdk.auth.aksk.server.exception;

import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

/**
 * Simple AKSK Server Base Exception
 *
 * @author surezzzzzz
 */
public class SimpleAkskServerException extends AkskException {

    public SimpleAkskServerException(String message) {
        super(message);
    }

    public SimpleAkskServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public SimpleAkskServerException(String errorCode, String message) {
        super(errorCode, message);
    }

    public SimpleAkskServerException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
