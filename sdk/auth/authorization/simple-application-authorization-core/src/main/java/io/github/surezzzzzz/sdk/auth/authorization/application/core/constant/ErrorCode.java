package io.github.surezzzzzz.sdk.auth.authorization.application.core.constant;

/**
 * 应用授权错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 授权协议无效。
     */
    public static final String INVALID_PROTOCOL = "BIZ_001";
    /**
     * 授权协议版本不支持。
     */
    public static final String UNSUPPORTED_VERSION = "BIZ_002";
    /**
     * 授权上下文无效。
     */
    public static final String INVALID_CONTEXT = "BIZ_003";
    /**
     * 授权撤销事件无效。
     */
    public static final String INVALID_REVOCATION_EVENT = "BIZ_004";

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleApplicationAuthorizationConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
