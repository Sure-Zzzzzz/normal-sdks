package io.github.surezzzzzz.sdk.mail.exception;

import lombok.Getter;

/**
 * Mail 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class MailClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public MailClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MailClientException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
