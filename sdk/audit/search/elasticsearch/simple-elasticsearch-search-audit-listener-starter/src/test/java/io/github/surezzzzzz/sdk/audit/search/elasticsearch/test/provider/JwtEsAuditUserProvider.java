package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 从JWT Token获取用户信息的Provider
 *
 * <p>用于JWT认证集成测试，从Spring Security的Authentication对象中解析JWT claims
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(
        prefix = "test.es.audit",
        name = "provider-type",
        havingValue = "jwt"
)
@Slf4j
public class JwtEsAuditUserProvider implements EsAuditUserProvider {

    @Override
    public String getClientId() {
        return getJwtClaim(SimpleAkskResourceConstant.JWT_CLAIM_CLIENT_ID);
    }

    @Override
    public String getClientType() {
        return getJwtClaim(SimpleAkskResourceConstant.JWT_CLAIM_CLIENT_TYPE);
    }

    @Override
    public String getUserId() {
        return getJwtClaim(SimpleAkskResourceConstant.JWT_CLAIM_USER_ID);
    }

    @Override
    public String getUsername() {
        return getJwtClaim(SimpleAkskResourceConstant.JWT_CLAIM_USERNAME);
    }

    private String getJwtClaim(String claimName) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken) {
                Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
                Object claim = jwt.getClaim(claimName);
                return claim != null ? claim.toString() : null;
            }
            log.debug("Authentication is not JwtAuthenticationToken: {}",
                    authentication != null ? authentication.getClass().getName() : "null");
            return null;
        } catch (Exception e) {
            log.warn("Failed to get JWT claim: {}", claimName, e);
            return null;
        }
    }
}
