package io.github.surezzzzzz.sdk.auth.resource.core.support;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationOutcome;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.SimpleResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.exception.ResourceAuthenticationException;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;

/**
 * 已验证资源上下文创建帮助类。
 *
 * @author surezzzzzz
 */
public final class ResourceAuthenticationContextHelper {

    private ResourceAuthenticationContextHelper() {
        throw new UnsupportedOperationException(SimpleResourceServerConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 从成功认证结果创建已验证资源上下文。
     *
     * @param result    成功认证结果
     * @param requestId 请求关联标识
     * @return 已验证资源上下文
     */
    public static VerifiedResourceContext createVerifiedContext(ResourceAuthenticationResult result, String requestId) {
        if (result == null || result.getOutcome() != ResourceAuthenticationOutcome.AUTHENTICATED
                || result.getPrincipal() == null || result.getApplicationAuthorization() == null) {
            throw new ResourceAuthenticationException(ErrorCode.INVALID_RESOURCE_AUTHENTICATION_MODEL,
                    String.format(ErrorMessage.INVALID_RESOURCE_AUTHENTICATION_MODEL,
                            SimpleResourceServerConstant.DETAIL_AUTHENTICATION_RESULT_INVALID));
        }
        ApplicationAuthorizationContext authorization = result.getApplicationAuthorization();
        return new VerifiedResourceContext(result.getPrincipal(), authorization, requestId);
    }
}
