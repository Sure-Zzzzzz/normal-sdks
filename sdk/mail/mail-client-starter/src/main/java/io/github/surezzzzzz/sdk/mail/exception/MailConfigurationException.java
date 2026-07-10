package io.github.surezzzzzz.sdk.mail.exception;

/**
 * Mail 配置异常
 *
 * @author surezzzzzz
 */
public class MailConfigurationException extends MailClientException {

    private static final long serialVersionUID = 1L;

    public MailConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public MailConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
