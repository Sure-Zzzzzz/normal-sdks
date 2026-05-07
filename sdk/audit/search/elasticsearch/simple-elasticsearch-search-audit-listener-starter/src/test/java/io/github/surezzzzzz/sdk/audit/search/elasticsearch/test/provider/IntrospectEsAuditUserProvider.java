package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

/**
 * 从 OAuth2 Introspection 获取用户信息的 Provider
 *
 * <p>用于 INTROSPECT 认证模式，从 Spring Security 的 Authentication 对象中解析 Introspection 响应。
 * INTROSPECT 模式下 Authentication 的实现类是 {@link org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal}。
 *
 * @author surezzzzzz
 * @since 1.0.2
 */
@Component
@ConditionalOnProperty(
        prefix = "test.es.audit",
        name = "provider-type",
        havingValue = "introspect"
)
@Slf4j
public class IntrospectEsAuditUserProvider implements EsAuditUserProvider {

    @Override
    public String getClientId() {
        return getClaim(SimpleAkskResourceConstant.JWT_CLAIM_CLIENT_ID);
    }

    @Override
    public String getClientType() {
        return getClaim(SimpleAkskResourceConstant.JWT_CLAIM_CLIENT_TYPE);
    }

    @Override
    public String getUserId() {
        return getClaim(SimpleAkskResourceConstant.JWT_CLAIM_USER_ID);
    }

    @Override
    public String getUsername() {
        return getClaim(SimpleAkskResourceConstant.JWT_CLAIM_USERNAME);
    }

    private String getClaim(String claimName) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return null;
            }

            Object principal = authentication.getPrincipal();
            if (!(principal instanceof OAuth2AuthenticatedPrincipal)) {
                return null;
            }

            OAuth2AuthenticatedPrincipal oauth2Principal = (OAuth2AuthenticatedPrincipal) principal;
            Object claim = oauth2Principal.getAttribute(claimName);
            return claim != null ? claim.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to get claim: {}", claimName, e);
            return null;
        }
    }
}
