package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 外部依赖故障异常（503，fail-close：introspect/MySQL/S3）
 *
 * @author surezzzzzz
 */
public class FeedbackDependencyException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public FeedbackDependencyException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FeedbackDependencyException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
