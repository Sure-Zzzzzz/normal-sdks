package io.github.surezzzzzz.sdk.auth.iam.core.constant;

/**
 * IAM核心错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * IAM路由键无效。
     */
    public static final String INVALID_ROUTE_KEY = "BIZ_001";

    /**
     * 外部身份源判定凭据错误。
     */
    public static final String EXTERNAL_BAD_CREDENTIALS = "BIZ_002";

    /**
     * 外部身份源不可达或故障。
     */
    public static final String EXTERNAL_PROVIDER_UNAVAILABLE = "BIZ_003";

    /**
     * 跳转型登录回调参数无效（缺失、state 不符或换取身份失败）。
     */
    public static final String EXTERNAL_CALLBACK_INVALID = "BIZ_004";

    /**
     * 外部身份未绑定本地用户（需管理员预绑定）。
     */
    public static final String EXTERNAL_IDENTITY_NOT_BOUND = "BIZ_005";

    /**
     * 外部身份缺少可用的用户名。
     */
    public static final String EXTERNAL_IDENTITY_USERNAME_INVALID = "BIZ_006";

    /**
     * 登录方式编码未注册或未启用。
     */
    public static final String EXTERNAL_PROVIDER_NOT_FOUND = "BIZ_007";

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleIamCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
