package io.github.surezzzzzz.sdk.auth.resource.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.SimpleResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.exception.ResourceAuthenticationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 已验证资源请求上下文。
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class VerifiedResourceContext {

    private final VerifiedResourcePrincipal principal;
    private final ApplicationAuthorizationContext applicationAuthorization;
    private final String requestId;

    /**
     * 创建已验证资源请求上下文。
     *
     * @param principal                已验证主体
     * @param applicationAuthorization 应用授权快照
     * @param requestId                请求关联标识
     */
    public VerifiedResourceContext(VerifiedResourcePrincipal principal,
                                   ApplicationAuthorizationContext applicationAuthorization, String requestId) {
        if (principal == null || applicationAuthorization == null || requestId == null || requestId.isEmpty()
                || Character.isWhitespace(requestId.charAt(0))
                || Character.isWhitespace(requestId.charAt(requestId.length() - 1))
                || requestId.codePointCount(0, requestId.length())
                > SimpleResourceServerConstant.MAX_IDENTIFIER_CODE_POINT_COUNT) {
            throw invalid(SimpleResourceServerConstant.DETAIL_CANNOT_BE_NULL);
        }
        if (!principal.getSubjectId().equals(applicationAuthorization.getSubjectId())
                || !isSameSubjectType(principal.getSubjectType(), applicationAuthorization.getSubjectType())) {
            throw invalid(SimpleResourceServerConstant.DETAIL_PRINCIPAL_AND_AUTHORIZATION_SUBJECT_MISMATCH);
        }
        this.principal = principal;
        this.applicationAuthorization = applicationAuthorization;
        this.requestId = requestId;
    }

    private static boolean isSameSubjectType(ResourceSubjectType resourceSubjectType,
                                             ApplicationAuthorizationSubjectType authorizationSubjectType) {
        return (resourceSubjectType == ResourceSubjectType.HUMAN
                && authorizationSubjectType == ApplicationAuthorizationSubjectType.HUMAN)
                || (resourceSubjectType == ResourceSubjectType.SERVICE
                && authorizationSubjectType == ApplicationAuthorizationSubjectType.SERVICE);
    }

    private static ResourceAuthenticationException invalid(String detail) {
        return new ResourceAuthenticationException(ErrorCode.INVALID_RESOURCE_AUTHENTICATION_MODEL,
                String.format(ErrorMessage.INVALID_RESOURCE_AUTHENTICATION_MODEL, detail));
    }
}
