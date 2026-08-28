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
 * 测试用 stub 内省器：只剪 introspect 网络边界，返回固定的 AKSK 内省声明。
 *
 * @author surezzzzzz
 */
@Configuration
public class StubAkskIntrospectorConfig {

    public static final String AKSK_SUBJECT = "service-client";
    public static final String APPLICATION_CODE = "resource-app";
    public static final String API_PERMISSION = "resource.read";

    private static Map<String, Object> activeClaims() {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.SERVICE,
                AKSK_SUBJECT,
                APPLICATION_CODE,
                true,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.singletonList(API_PERMISSION),
                null,
                1L,
                "audit-manifest",
                "audit-digest",
                now.minusSeconds(1L),
                now.plusSeconds(60L));
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put(AkskResourceIntrospectionClaimConstant.ACTIVE, Boolean.TRUE);
        claims.put(JwtClaimConstant.CLIENT_ID, AKSK_SUBJECT);
        claims.put(JwtClaimConstant.APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
        return claims;
    }

    @Bean(name = "akskOpaqueTokenIntrospector")
    OpaqueTokenIntrospector akskOpaqueTokenIntrospector() {
        return token -> new DefaultOAuth2AuthenticatedPrincipal(
                AKSK_SUBJECT, activeClaims(), Collections.emptyList());
    }
}
