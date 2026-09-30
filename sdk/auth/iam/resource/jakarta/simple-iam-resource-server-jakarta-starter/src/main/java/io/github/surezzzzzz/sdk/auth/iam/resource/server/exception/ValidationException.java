package io.github.surezzzzzz.sdk.auth.iam.resource.server.exception;

import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.ErrorCode;

/**
 * Validation Exception
 *
 * @author surezzzzzz
 */
public class ValidationException extends SimpleIamResourceServerException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
