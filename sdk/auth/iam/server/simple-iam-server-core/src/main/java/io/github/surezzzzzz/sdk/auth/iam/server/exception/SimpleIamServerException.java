package io.github.surezzzzzz.sdk.auth.iam.server.exception;

import lombok.Getter;

/**
 * Simple IAM Server Base Exception
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleIamServerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleIamServerException(String message) {
        super(message);
        this.errorCode = null;
    }

    public SimpleIamServerException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public SimpleIamServerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleIamServerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
