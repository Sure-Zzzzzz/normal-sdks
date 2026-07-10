package io.github.surezzzzzz.sdk.mail.exception;

/**
 * Mail 附件异常
 *
 * @author surezzzzzz
 */
public class MailAttachmentException extends MailClientException {

    private static final long serialVersionUID = 1L;

    public MailAttachmentException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MailAttachmentException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
