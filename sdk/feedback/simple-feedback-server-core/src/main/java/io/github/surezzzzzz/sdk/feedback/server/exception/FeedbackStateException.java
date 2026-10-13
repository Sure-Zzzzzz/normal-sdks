package io.github.surezzzzzz.sdk.feedback.server.exception;

/**
 * 状态机迁移异常（409）
 *
 * @author surezzzzzz
 */
public class FeedbackStateException extends SimpleFeedbackServerException {

    private static final long serialVersionUID = 1L;

    public FeedbackStateException(String errorCode, String message) {
        super(errorCode, message);
    }
}
