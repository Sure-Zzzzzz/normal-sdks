package io.github.surezzzzzz.sdk.auth.iam.resource.server.exception;

import lombok.Getter;

/**
 * Simple IAM Resource Server Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleIamResourceServerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleIamResourceServerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleIamResourceServerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
