package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.exception;

import lombok.Getter;

/**
 * Simple IAM OIDC Adapter Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleIamOidcAdapterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleIamOidcAdapterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleIamOidcAdapterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
