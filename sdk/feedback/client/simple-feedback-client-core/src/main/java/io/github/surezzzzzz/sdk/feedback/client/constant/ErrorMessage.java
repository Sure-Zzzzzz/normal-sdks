package io.github.surezzzzzz.sdk.feedback.client.constant;

/** Feedback 客户端异常消息模板。 */
public final class ErrorMessage {
    public static final String UTILITY_CLASS = "工具类不允许实例化";
    public static final String CLIENT_PARAMETER_INVALID = "Feedback 客户端参数非法: %s";
    public static final String RESPONSE_FIELD_INVALID = "Feedback response field invalid: %s";
    public static final String REQUEST_ENCODING_FAILED = "Feedback 请求编码失败";
    public static final String RESPONSE_ENCODING_INVALID = "Feedback 响应编码非法";
    public static final String SERVICE_UNAVAILABLE = "Feedback 服务暂不可用";
    public static final String NETWORK_UNAVAILABLE = "Feedback 网络不可达";
    public static final String REQUEST_REJECTED = "Feedback 请求被拒绝";

    private ErrorMessage() {
        throw new UnsupportedOperationException(UTILITY_CLASS);
    }
}
