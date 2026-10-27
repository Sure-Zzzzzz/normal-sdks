package io.github.surezzzzzz.sdk.feedback.client.constant;

/** Feedback 客户端协议常量。 */
public final class FeedbackClientConstant {
    public static final String API_BASE_PATH = "/api/feedback";
    public static final int SUCCESS_STATUS = 200;
    public static final int CLIENT_ENCODING_STATUS = 400;
    public static final int NETWORK_FAILURE_STATUS = 0;
    public static final int TOO_MANY_REQUESTS_STATUS = 429;
    public static final int SERVER_ERROR_STATUS = 500;
    private FeedbackClientConstant() { throw new UnsupportedOperationException(ErrorMessage.UTILITY_CLASS); }
}
