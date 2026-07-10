package io.github.surezzzzzz.sdk.mail.exception;

/**
 * Mail 解析异常
 *
 * @author surezzzzzz
 */
public class MailParseException extends MailClientException {

    private static final long serialVersionUID = 1L;

    public MailParseException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MailParseException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
