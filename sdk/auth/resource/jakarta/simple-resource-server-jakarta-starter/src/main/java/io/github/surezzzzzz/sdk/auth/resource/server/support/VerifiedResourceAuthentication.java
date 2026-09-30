package io.github.surezzzzzz.sdk.auth.resource.server.support;

import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

/**
 * Spring Security已验证资源认证态。
 *
 * @author surezzzzzz
 */
public final class VerifiedResourceAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final VerifiedResourceContext context;

    /**
     * 创建已验证资源认证态。
     *
     * @param context 已验证资源上下文
     */
    public VerifiedResourceAuthentication(VerifiedResourceContext context) {
        super(Collections.emptyList());
        this.context = context;
        setAuthenticated(true);
    }

    /**
     * 不保留凭据。
     *
     * @return 固定脱敏文本
     */
    @Override
    public Object getCredentials() {
        return SimpleResourceServerStarterConstant.ANONYMOUS_PRINCIPAL;
    }

    /**
     * 返回已验证资源上下文。
     *
     * @return 已验证资源上下文
     */
    @Override
    public Object getPrincipal() {
        return context;
    }
}
