package io.github.surezzzzzz.sdk.auth.captcha.exception;

import lombok.Getter;

/**
 * Simple Captcha Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleCaptchaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleCaptchaException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleCaptchaException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
