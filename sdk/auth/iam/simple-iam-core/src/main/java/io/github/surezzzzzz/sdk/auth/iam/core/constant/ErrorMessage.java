package io.github.surezzzzzz.sdk.auth.iam.core.constant;

/**
 * IAM核心错误信息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * IAM路由键无效。
     */
    public static final String INVALID_ROUTE_KEY = "IAM路由键无效：%s";

    /**
     * 外部身份源凭据错误。
     */
    public static final String EXTERNAL_BAD_CREDENTIALS = "外部身份源凭据错误";

    /**
     * 外部身份源不可达或故障。
     */
    public static final String EXTERNAL_PROVIDER_UNAVAILABLE = "外部身份源暂不可用";

    /**
     * 跳转型登录回调参数无效。
     */
    public static final String EXTERNAL_CALLBACK_INVALID = "外部登录回调校验未通过";

    /**
     * 外部身份未绑定本地用户。
     */
    public static final String EXTERNAL_IDENTITY_NOT_BOUND = "外部身份未绑定本地账号";

    /**
     * 外部身份缺少可用的用户名。
     */
    public static final String EXTERNAL_IDENTITY_USERNAME_INVALID = "外部身份缺少可用的用户名";

    /**
     * 登录方式编码未注册或未启用。
     */
    public static final String EXTERNAL_PROVIDER_NOT_FOUND = "登录方式不存在：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleIamCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
