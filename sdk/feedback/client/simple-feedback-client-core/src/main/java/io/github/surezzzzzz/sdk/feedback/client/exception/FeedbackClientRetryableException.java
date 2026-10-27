package io.github.surezzzzzz.sdk.feedback.client.exception;

/** 网络、5xx 或 429 等可由调用方按策略重试的异常。 */
public class FeedbackClientRetryableException extends FeedbackClientException {
    private static final long serialVersionUID = 1L;
    private final int statusCode;
    public FeedbackClientRetryableException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
    public int getStatusCode() { return statusCode; }
}
