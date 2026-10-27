package io.github.surezzzzzz.sdk.feedback.client.exception;

/** 4xx 或响应结构不符合契约时抛出的不可重试异常。 */
public class FeedbackClientProtocolException extends FeedbackClientException {
    private static final long serialVersionUID = 1L;
    private final int statusCode;
    private final String correlationId;
    public FeedbackClientProtocolException(int statusCode, String message, String correlationId) {
        super(message);
        this.statusCode = statusCode;
        this.correlationId = correlationId;
    }
    public int getStatusCode() { return statusCode; }
    public String getCorrelationId() { return correlationId; }
}
