package io.github.surezzzzzz.sdk.auth.aksk.server.exception;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;

/**
 * Validation Exception
 *
 * @author surezzzzzz
 */
public class ValidationException extends SimpleAkskServerException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }

    public ValidationException(String message, Throwable cause) {
        super(ErrorCode.VALIDATION_FAILED, message, cause);
    }
}
