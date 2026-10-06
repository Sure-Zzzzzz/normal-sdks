package io.github.surezzzzzz.sdk.audit.aksk.test.config;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.AkskResourceIntrospectionClaimConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 仅替代令牌内省的网络边界，认证来源识别、权限翻译和事件发布均使用正式组件。
 *
 * @author surezzzzzz
 */
@Configuration(proxyBeanMethods = false)
public class StubAkskIntrospectorConfiguration {
    @Bean(name = "akskOpaqueTokenIntrospector")
    OpaqueTokenIntrospector akskOpaqueTokenIntrospector() {
        return token -> {
            Instant now = Instant.now();
            ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                    SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                    ApplicationAuthorizationSubjectType.SERVICE, "service-client", "resource-app", true,
                    Collections.emptyList(), Collections.emptyList(), Collections.singletonList("resource.read"),
                    null, 1L, "audit-manifest", "audit-digest", now.minusSeconds(1), now.plusSeconds(60));
            Map<String, Object> claims = new HashMap<>();
            claims.put(AkskResourceIntrospectionClaimConstant.ACTIVE, Boolean.TRUE);
            claims.put(JwtClaimConstant.CLIENT_ID, "service-client");
            claims.put(JwtClaimConstant.APPLICATION_AUTHORIZATION,
                    ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
            return new DefaultOAuth2AuthenticatedPrincipal("service-client", claims, Collections.emptyList());
        };
    }
}
