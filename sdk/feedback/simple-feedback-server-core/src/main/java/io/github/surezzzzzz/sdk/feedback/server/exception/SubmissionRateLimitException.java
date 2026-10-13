package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 提交频控超限异常（429）
 *
 * @author surezzzzzz
 */
public class SubmissionRateLimitException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public SubmissionRateLimitException(String errorCode, String message) {
        super(errorCode, message);
    }
}
