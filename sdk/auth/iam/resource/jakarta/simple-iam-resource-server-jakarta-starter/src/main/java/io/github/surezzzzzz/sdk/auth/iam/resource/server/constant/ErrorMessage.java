package io.github.surezzzzzz.sdk.auth.iam.resource.server.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 参数验证失败
     */
    public static final String VALIDATION_FAILED = "参数验证失败";

    /**
     * 配置验证失败
     */
    public static final String CONFIG_VALIDATION_FAILED = "配置验证失败";

    /**
     * 受控验证响应协议无效
     */
    public static final String VERIFICATION_PROTOCOL_INVALID = "IAM受控验证响应不符合协议";

    /**
     * 受控验证服务不可用
     */
    public static final String VERIFICATION_UNAVAILABLE = "IAM受控验证服务不可用";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
