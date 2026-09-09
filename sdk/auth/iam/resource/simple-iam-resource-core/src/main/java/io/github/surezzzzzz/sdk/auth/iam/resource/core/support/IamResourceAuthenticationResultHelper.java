package io.github.surezzzzzz.sdk.auth.iam.resource.core.support;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.SimpleIamResourceConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.exception.IamResourceAuthenticationException;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.model.IamVerifiedAuthentication;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;

/**
 * IAM资源认证结果帮助类。
 *
 * @author surezzzzzz
 */
public final class IamResourceAuthenticationResultHelper {

    private IamResourceAuthenticationResultHelper() {
        throw new UnsupportedOperationException(SimpleIamResourceConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 将IAM认证信息转换为公共资源认证结果。
     *
     * @param authentication 已完整验证的IAM认证信息
     * @return 公共资源认证结果
     */
    public static ResourceAuthenticationResult authenticated(IamVerifiedAuthentication authentication) {
        if (authentication == null) {
            throw invalid(String.format(SimpleIamResourceConstant.DETAIL_CANNOT_BE_NULL, SimpleIamResourceConstant.FIELD_AUTHENTICATION));
        }
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID),
                ResourceSubjectType.HUMAN, authentication.getSubjectId());
        return ResourceAuthenticationResult.authenticated(principal, authentication.getApplicationAuthorization());
    }

    private static IamResourceAuthenticationException invalid(String detail) {
        return new IamResourceAuthenticationException(ErrorCode.INVALID_VERIFIED_AUTHENTICATION,
                String.format(ErrorMessage.INVALID_VERIFIED_AUTHENTICATION, detail));
    }
}
