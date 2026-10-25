package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 应用编码重复登记异常。
 *
 * @author surezzzzzz
 */
public class ApplicationAlreadyRegisteredException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public ApplicationAlreadyRegisteredException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ApplicationAlreadyRegisteredException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
