package io.github.surezzzzzz.sdk.auth.data.permission.core.constant;

/**
 * 数据权限错误信息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 授权协议无效提示。
     */
    public static final String INVALID_PROTOCOL = "授权协议无效：%s";
    /**
     * 授权协议版本不支持提示。
     */
    public static final String UNSUPPORTED_VERSION = "授权协议版本不支持：%s";
    /**
     * 授权项无效提示。
     */
    public static final String INVALID_GRANT = "授权项无效：%s";
    /**
     * 授权约束无效提示。
     */
    public static final String INVALID_CONSTRAINT = "授权约束无效：%s";
    /**
     * 授权文档无效提示。
     */
    public static final String INVALID_DOCUMENT = "授权文档无效：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleDataPermissionConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
