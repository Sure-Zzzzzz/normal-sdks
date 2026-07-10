package io.github.surezzzzzz.sdk.mail.exception;

/**
 * Mail 参数校验异常
 *
 * @author surezzzzzz
 */
public class MailValidationException extends MailClientException {

    private static final long serialVersionUID = 1L;

    public MailValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MailValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
