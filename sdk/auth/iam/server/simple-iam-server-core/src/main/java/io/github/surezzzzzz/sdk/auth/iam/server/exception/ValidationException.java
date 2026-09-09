package io.github.surezzzzzz.sdk.auth.iam.server.exception;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;

/**
 * Validation Exception
 *
 * @author surezzzzzz
 */
public class ValidationException extends SimpleIamServerException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }

    public ValidationException(String message, Throwable cause) {
        super(ErrorCode.VALIDATION_FAILED, message, cause);
    }
}
