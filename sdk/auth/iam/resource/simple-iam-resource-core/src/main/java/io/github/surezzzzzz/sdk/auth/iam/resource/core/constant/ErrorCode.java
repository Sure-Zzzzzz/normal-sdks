package io.github.surezzzzzz.sdk.auth.iam.resource.core.constant;

/**
 * IAM资源核心错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * IAM已验证认证信息无效。
     */
    public static final String INVALID_VERIFIED_AUTHENTICATION = "BIZ_001";

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleIamResourceConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
