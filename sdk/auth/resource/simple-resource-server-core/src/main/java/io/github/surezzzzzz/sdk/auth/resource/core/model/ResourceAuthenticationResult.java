package io.github.surezzzzzz.sdk.auth.resource.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.*;
import io.github.surezzzzzz.sdk.auth.resource.core.exception.ResourceAuthenticationException;
import lombok.Getter;

/**
 * 资源认证结果。
 *
 * @author surezzzzzz
 */
@Getter
public final class ResourceAuthenticationResult {

    private final ResourceAuthenticationOutcome outcome;
    private final VerifiedResourcePrincipal principal;
    private final ApplicationAuthorizationContext applicationAuthorization;
    private final ResourceAuthenticationFailureCategory failureCategory;

    private ResourceAuthenticationResult(ResourceAuthenticationOutcome outcome, VerifiedResourcePrincipal principal,
                                         ApplicationAuthorizationContext applicationAuthorization,
                                         ResourceAuthenticationFailureCategory failureCategory) {
        this.outcome = outcome;
        this.principal = principal;
        this.applicationAuthorization = applicationAuthorization;
        this.failureCategory = failureCategory;
    }

    /**
     * 创建认证成功结果。
     *
     * @param principal     已验证主体
     * @param authorization 已验证应用授权快照
     * @return 认证成功结果
     */
    public static ResourceAuthenticationResult authenticated(VerifiedResourcePrincipal principal,
                                                             ApplicationAuthorizationContext authorization) {
        if (principal == null || authorization == null) {
            throw invalid();
        }
        return new ResourceAuthenticationResult(ResourceAuthenticationOutcome.AUTHENTICATED, principal, authorization, null);
    }

    /**
     * 创建认证拒绝结果。
     *
     * @param failureCategory 安全失败分类
     * @return 认证拒绝结果
     */
    public static ResourceAuthenticationResult rejected(ResourceAuthenticationFailureCategory failureCategory) {
        if (failureCategory == null) {
            throw invalid();
        }
        return new ResourceAuthenticationResult(ResourceAuthenticationOutcome.REJECTED, null, null, failureCategory);
    }

    /**
     * 创建当前适配器不适用结果。
     *
     * @return 当前适配器不适用结果
     */
    public static ResourceAuthenticationResult notApplicable() {
        return new ResourceAuthenticationResult(ResourceAuthenticationOutcome.NOT_APPLICABLE, null, null, null);
    }

    private static ResourceAuthenticationException invalid() {
        return new ResourceAuthenticationException(ErrorCode.INVALID_RESOURCE_AUTHENTICATION_MODEL,
                String.format(ErrorMessage.INVALID_RESOURCE_AUTHENTICATION_MODEL,
                        SimpleResourceServerConstant.DETAIL_AUTHENTICATION_RESULT_INVALID));
    }
}
