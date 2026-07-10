package io.github.surezzzzzz.sdk.mail.exception;

/**
 * Mail 读取异常
 *
 * @author surezzzzzz
 */
public class MailReadException extends MailClientException {

    private static final long serialVersionUID = 1L;

    public MailReadException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MailReadException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
