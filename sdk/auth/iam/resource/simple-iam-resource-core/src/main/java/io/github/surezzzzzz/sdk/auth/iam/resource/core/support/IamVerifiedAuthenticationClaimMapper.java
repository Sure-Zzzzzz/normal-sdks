package io.github.surezzzzzz.sdk.auth.iam.resource.core.support;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.SimpleIamResourceConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.exception.IamResourceAuthenticationException;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.model.IamVerifiedAuthentication;

import java.util.Map;

/**
 * IAM已验证认证Claim映射器。
 *
 * @author surezzzzzz
 */
public final class IamVerifiedAuthenticationClaimMapper {

    private IamVerifiedAuthenticationClaimMapper() {
        throw new UnsupportedOperationException(SimpleIamResourceConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 从已完整验证的IAM Claim创建认证信息。
     *
     * @param claims 已完整验证的IAM Claim集合
     * @return IAM认证信息
     */
    public static IamVerifiedAuthentication fromVerifiedClaims(Map<String, Object> claims) {
        if (claims == null) {
            throw invalid(String.format(SimpleIamResourceConstant.DETAIL_CANNOT_BE_NULL, SimpleIamResourceConstant.FIELD_CLAIMS));
        }
        Object subjectClaim = claims.get(SimpleIamCoreConstant.CLAIM_SUBJECT);
        if (!(subjectClaim instanceof String)) {
            throw invalid(SimpleIamResourceConstant.DETAIL_SUBJECT_CLAIM_MUST_BE_TEXT);
        }
        ApplicationAuthorizationContext authorization;
        try {
            authorization = ApplicationAuthorizationContextClaimMapper.fromClaim(
                    claims.get(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION));
        } catch (RuntimeException exception) {
            throw invalid(SimpleIamResourceConstant.DETAIL_APPLICATION_AUTHORIZATION_CLAIM_INVALID);
        }
        String subjectId = (String) subjectClaim;
        if (authorization.getSubjectType() != ApplicationAuthorizationSubjectType.HUMAN) {
            throw invalid(SimpleIamResourceConstant.DETAIL_AUTHORIZATION_SUBJECT_TYPE_INVALID);
        }
        if (!subjectId.equals(authorization.getSubjectId())) {
            throw invalid(SimpleIamResourceConstant.DETAIL_SUBJECT_MISMATCH);
        }
        return new IamVerifiedAuthentication(subjectId, authorization);
    }

    private static IamResourceAuthenticationException invalid(String detail) {
        return new IamResourceAuthenticationException(ErrorCode.INVALID_VERIFIED_AUTHENTICATION,
                String.format(ErrorMessage.INVALID_VERIFIED_AUTHENTICATION, detail));
    }
}
