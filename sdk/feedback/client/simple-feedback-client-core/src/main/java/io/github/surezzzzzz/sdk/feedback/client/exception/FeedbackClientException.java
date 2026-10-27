package io.github.surezzzzzz.sdk.feedback.client.exception;

/** Feedback 客户端基础异常。 */
public class FeedbackClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public FeedbackClientException(String message) { super(message); }
    public FeedbackClientException(String message, Throwable cause) { super(message, cause); }
}
