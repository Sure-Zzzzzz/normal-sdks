package io.github.surezzzzzz.sdk.auth.iam.resource.server.constant;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 参数验证失败
     */
    public static final String VALIDATION_FAILED = "VALIDATION_001";

    /**
     * 配置验证失败
     */
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_001";

    /**
     * 受控验证响应协议无效
     */
    public static final String VERIFICATION_PROTOCOL_INVALID = "VERIFICATION_001";

    /**
     * 受控验证服务不可用
     */
    public static final String VERIFICATION_UNAVAILABLE = "VERIFICATION_002";

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
