package io.github.surezzzzzz.sdk.template.doc.exception;

import lombok.Getter;

/**
 * Simple Doc Template Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleDocTemplateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleDocTemplateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleDocTemplateException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}