package io.github.surezzzzzz.sdk.sensitive.keyword.exception;

import lombok.Getter;

/**
 * Simple Keyword Sensitive Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class SmartKeywordSensitiveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SmartKeywordSensitiveException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SmartKeywordSensitiveException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
