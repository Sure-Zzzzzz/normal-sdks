package io.github.surezzzzzz.sdk.auth.iam.resource.server.constant;

/**
 * Simple IAM Resource Server 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleIamResourceServerConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.iam.resource.server";
    /**
     * 验证端点路径。
     */
    public static final String TOKEN_VERIFICATION_PATH = "/iam/resource/tokens/verify";
    /**
     * Basic认证方案。
     */
    public static final String BASIC_AUTHENTICATION_SCHEME = "Basic";
    /**
     * HTTP未认证状态。
     */
    public static final int HTTP_STATUS_UNAUTHORIZED = 401;
    /**
     * HTTP成功状态。
     */
    public static final int HTTP_STATUS_OK = 200;
    /**
     * 常量类实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";

    private SimpleIamResourceServerConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
