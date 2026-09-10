package io.github.surezzzzzz.sdk.audit.iam.test.config;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.HttpIamResourceTokenVerificationClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用 stub 受控验证客户端：只剪 IAM verify 网络边界，返回固定的双字段成功响应。
 *
 * @author surezzzzzz
 */
@Configuration
public class StubIamVerificationClientConfig {

    public static final String IAM_SUBJECT = "user-a";
    public static final String APPLICATION_CODE = "resource-app";
    public static final String API_PERMISSION = "resource.read";

    private static Map<String, Object> verifiedClaims() {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN,
                IAM_SUBJECT,
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
        Map<String, Object> claims = new LinkedHashMap<String, Object>();
        claims.put(SimpleIamCoreConstant.CLAIM_SUBJECT, IAM_SUBJECT);
        claims.put(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
        return claims;
    }

    @Bean
    HttpIamResourceTokenVerificationClient iamResourceTokenVerificationClient() {
        SimpleIamResourceServerProperties properties = new SimpleIamResourceServerProperties();
        properties.setVerificationEndpoint("http://iam.example/iam/resource/tokens/verify");
        properties.setClientId("verifier");
        properties.setClientSecret("secret");
        return new HttpIamResourceTokenVerificationClient(properties) {
            @Override
            public Map<String, Object> verify(String token) {
                return verifiedClaims();
            }
        };
    }
}
