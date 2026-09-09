package io.github.surezzzzzz.sdk.auth.iam.resource.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.constant.SimpleIamResourceConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.core.exception.IamResourceAuthenticationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 已完整验证的IAM认证信息。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public final class IamVerifiedAuthentication {

    /**
     * IAM稳定用户标识。
     */
    private final String subjectId;
    /**
     * 已验证的应用授权快照。
     */
    private final ApplicationAuthorizationContext applicationAuthorization;

    /**
     * 创建已完整验证的IAM认证信息。
     *
     * @param subjectId                IAM稳定用户标识
     * @param applicationAuthorization 已验证的应用授权快照
     */
    public IamVerifiedAuthentication(String subjectId, ApplicationAuthorizationContext applicationAuthorization) {
        if (subjectId == null || subjectId.isEmpty()) {
            throw invalid(String.format(SimpleIamResourceConstant.DETAIL_CANNOT_BE_NULL,
                    SimpleIamResourceConstant.FIELD_SUBJECT_ID));
        }
        if (applicationAuthorization == null) {
            throw invalid(String.format(SimpleIamResourceConstant.DETAIL_CANNOT_BE_NULL,
                    SimpleIamResourceConstant.FIELD_APPLICATION_AUTHORIZATION));
        }
        if (applicationAuthorization.getSubjectType() != ApplicationAuthorizationSubjectType.HUMAN) {
            throw invalid(SimpleIamResourceConstant.DETAIL_AUTHORIZATION_SUBJECT_TYPE_INVALID);
        }
        if (!subjectId.equals(applicationAuthorization.getSubjectId())) {
            throw invalid(SimpleIamResourceConstant.DETAIL_SUBJECT_MISMATCH);
        }
        this.subjectId = subjectId;
        this.applicationAuthorization = applicationAuthorization;
    }

    private static IamResourceAuthenticationException invalid(String detail) {
        return new IamResourceAuthenticationException(ErrorCode.INVALID_VERIFIED_AUTHENTICATION,
                String.format(ErrorMessage.INVALID_VERIFIED_AUTHENTICATION, detail));
    }
}
