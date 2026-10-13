package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 应用注册项不可用异常（提交时 400：未命中/已停用）
 *
 * @author surezzzzzz
 */
public class ApplicationNotAvailableException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public ApplicationNotAvailableException(String errorCode, String message) {
        super(errorCode, message);
    }
}
