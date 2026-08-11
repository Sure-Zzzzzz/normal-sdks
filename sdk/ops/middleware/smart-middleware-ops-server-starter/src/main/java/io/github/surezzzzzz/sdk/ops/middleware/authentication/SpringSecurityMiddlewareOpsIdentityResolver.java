package io.github.surezzzzzz.sdk.ops.middleware.authentication;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 基于 Spring Security 已认证上下文的身份解析器。
 *
 * @author surezzzzzz
 */
public class SpringSecurityMiddlewareOpsIdentityResolver implements MiddlewareOpsIdentityResolver {

    @Override
    public MiddlewareOpsIdentity resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return MiddlewareOpsIdentity.builder().subject(authentication.getName())
                .displayName(authentication.getName()).authenticationMechanism("spring-security").build();
    }
}
