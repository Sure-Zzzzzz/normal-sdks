package io.github.surezzzzzz.sdk.auth.authorization.application.core.constant;

/**
 * 应用授权错误信息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 授权协议无效。
     */
    public static final String INVALID_PROTOCOL = "应用授权协议无效：%s";
    /**
     * 授权协议版本不支持。
     */
    public static final String UNSUPPORTED_VERSION = "应用授权协议版本不支持：%s";
    /**
     * 授权上下文无效。
     */
    public static final String INVALID_CONTEXT = "应用授权上下文无效：%s";
    /**
     * 授权撤销事件无效。
     */
    public static final String INVALID_REVOCATION_EVENT = "应用授权撤销事件无效：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleApplicationAuthorizationConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
