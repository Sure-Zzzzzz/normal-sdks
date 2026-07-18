package io.github.surezzzzzz.sdk.auth.data.permission.core.constant;

/**
 * 数据权限错误码。
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
     * 授权项无效。
     */
    public static final String INVALID_GRANT = "BIZ_003";
    /**
     * 授权约束无效。
     */
    public static final String INVALID_CONSTRAINT = "BIZ_004";
    /**
     * 授权文档无效。
     */
    public static final String INVALID_DOCUMENT = "BIZ_005";

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleDataPermissionConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
