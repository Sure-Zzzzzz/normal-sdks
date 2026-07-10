package io.github.surezzzzzz.sdk.mail.exception;

/**
 * Mail 发送异常
 *
 * @author surezzzzzz
 */
public class MailSendException extends MailClientException {

    private static final long serialVersionUID = 1L;

    public MailSendException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MailSendException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
