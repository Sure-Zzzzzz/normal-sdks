package io.github.surezzzzzz.sdk.auth.resource.server.support;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationOutcome;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;

import javax.servlet.http.HttpServletRequest;

/**
 * 默认资源服务认证编排引擎。
 *
 * @author surezzzzzz
 */
public final class DefaultResourceServerEngine implements ResourceServerEngine {

    private final BearerCredentialResolver credentialResolver;
    private final ResourceAuthenticationAdapterRegistry adapterRegistry;

    /**
     * 创建默认资源服务认证编排引擎。
     *
     * @param credentialResolver Bearer凭据解析器
     * @param adapterRegistry    认证适配器注册表
     */
    public DefaultResourceServerEngine(BearerCredentialResolver credentialResolver,
                                       ResourceAuthenticationAdapterRegistry adapterRegistry) {
        this.credentialResolver = credentialResolver;
        this.adapterRegistry = adapterRegistry;
    }

    /**
     * 对受保护请求执行唯一来源认证。
     *
     * @param request HTTP请求
     * @return 认证结果
     */
    @Override
    public ResourceAuthenticationResult authenticate(HttpServletRequest request) {
        BearerCredentialResolution resolution = credentialResolver.resolve(request);
        if (!resolution.isResolved()) {
            return ResourceAuthenticationResult.rejected(resolution.getFailureCategory());
        }
        ResourceAuthenticationAdapter adapter = adapterRegistry.get(resolution.getCredential().getSourceId());
        if (adapter == null) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.SOURCE_UNRECOGNIZED);
        }
        if (!adapter.sourceId().equals(resolution.getCredential().getSourceId())) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        ResourceAuthenticationResult result;
        try {
            result = adapter.authenticate(resolution.getCredential());
        } catch (RuntimeException exception) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.PROVIDER_UNAVAILABLE);
        }
        if (result == null || result.getOutcome() == ResourceAuthenticationOutcome.NOT_APPLICABLE) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        if (result.getOutcome() == ResourceAuthenticationOutcome.REJECTED) {
            return result;
        }
        if (result.getOutcome() != ResourceAuthenticationOutcome.AUTHENTICATED
                || result.getPrincipal() == null || result.getApplicationAuthorization() == null
                || !adapter.sourceId().equals(result.getPrincipal().getSourceId())) {
            return ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
        }
        return result;
    }
}
