package io.github.surezzzzzz.sdk.auth.iam.resource.core.constant;

/**
 * IAM资源核心错误信息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * IAM已验证认证信息无效。
     */
    public static final String INVALID_VERIFIED_AUTHENTICATION = "IAM已验证认证信息无效：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleIamResourceConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
