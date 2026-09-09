package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.exception;

import lombok.Getter;

/**
 * Simple IAM LDAP Adapter Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleIamLdapAdapterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleIamLdapAdapterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleIamLdapAdapterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
