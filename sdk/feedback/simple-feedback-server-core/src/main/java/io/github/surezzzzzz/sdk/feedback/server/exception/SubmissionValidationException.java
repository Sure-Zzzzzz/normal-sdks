package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 提交字段校验异常（400）
 *
 * @author surezzzzzz
 */
public class SubmissionValidationException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public SubmissionValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
