package io.github.surezzzzzz.sdk.auth.iam.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.IamResourceVerificationUnavailableException;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.HttpIamResourceTokenVerificationClient;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.IamResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationOutcome;
import io.github.surezzzzzz.sdk.auth.resource.core.model.BearerResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IAM资源认证适配器测试。
 *
 * @author surezzzzzz
 */
class IamResourceAuthenticationAdapterTest {

    @Test
    void shouldMapControlledVerificationResponseToHumanResourceContext() {
        IamResourceAuthenticationAdapter adapter = new IamResourceAuthenticationAdapter(stubClient(verifiedClaims()));

        ResourceAuthenticationResult result = adapter.authenticate(iamCredential());

        assertEquals(ResourceAuthenticationOutcome.AUTHENTICATED, result.getOutcome(),
                "受控验证成功必须创建公共认证结果");
        assertEquals("iam", result.getPrincipal().getSourceId().getValue(), "来源必须固定为IAM");
        assertEquals("user-a", result.getPrincipal().getSubjectId(), "必须保留稳定IAM用户主体");
    }

    @Test
    void shouldRejectInactiveOrMalformedVerificationResultsWithoutProviderFallback() {
        IamResourceAuthenticationAdapter inactiveAdapter = new IamResourceAuthenticationAdapter(stubClient(null));
        IamResourceAuthenticationAdapter malformedAdapter = new IamResourceAuthenticationAdapter(stubClient(Collections.emptyMap()));

        ResourceAuthenticationResult inactive = inactiveAdapter.authenticate(iamCredential());
        ResourceAuthenticationResult malformed = malformedAdapter.authenticate(iamCredential());

        assertRejected(inactive, ResourceAuthenticationFailureCategory.TOKEN_INACTIVE,
                "IAM拒绝时必须直接拒绝，不能要求其他Provider重试");
        assertRejected(malformed, ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID,
                "受控响应不满足IAM协议必须拒绝");
    }

    @Test
    void shouldClassifyControlledVerificationOutageAndWrongCredential() {
        IamResourceAuthenticationAdapter unavailableAdapter = new IamResourceAuthenticationAdapter(failingClient());
        IamResourceAuthenticationAdapter adapter = new IamResourceAuthenticationAdapter(stubClient(verifiedClaims()));
        BearerResourceCredential akskCredential = new BearerResourceCredential(
                new ResourceAuthenticationSourceId("aksk"), "token");

        assertRejected(unavailableAdapter.authenticate(iamCredential()),
                ResourceAuthenticationFailureCategory.PROVIDER_UNAVAILABLE,
                "IAM验证端点不可用必须拒绝");
        assertRejected(adapter.authenticate(akskCredential), ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED,
                "IAM适配器不得接受AKSK路由凭据");
    }

    private BearerResourceCredential iamCredential() {
        return new BearerResourceCredential(new ResourceAuthenticationSourceId("iam"), "token");
    }

    private HttpIamResourceTokenVerificationClient stubClient(final Map<String, Object> claims) {
        return new HttpIamResourceTokenVerificationClient(validProperties()) {
            @Override
            public Map<String, Object> verify(String token) {
                return claims;
            }
        };
    }

    private HttpIamResourceTokenVerificationClient failingClient() {
        return new HttpIamResourceTokenVerificationClient(validProperties()) {
            @Override
            public Map<String, Object> verify(String token) {
                throw new IamResourceVerificationUnavailableException();
            }
        };
    }

    private SimpleIamResourceServerProperties validProperties() {
        SimpleIamResourceServerProperties properties = new SimpleIamResourceServerProperties();
        properties.setVerificationEndpoint("http://iam.example/iam/resource/tokens/verify");
        properties.setClientId("verifier");
        properties.setClientSecret("secret");
        return properties;
    }

    private Map<String, Object> verifiedClaims() {
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN,
                "user-a",
                "application-a",
                true,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.singletonList("api.read"),
                null,
                1L,
                "manifest-1",
                "digest-a",
                Instant.ofEpochSecond(100L),
                Instant.ofEpochSecond(200L));
        Map<String, Object> claims = new LinkedHashMap<String, Object>();
        claims.put(SimpleIamCoreConstant.CLAIM_SUBJECT, "user-a");
        claims.put(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
        return claims;
    }

    private void assertRejected(ResourceAuthenticationResult result,
                                ResourceAuthenticationFailureCategory category, String message) {
        assertEquals(category, result.getFailureCategory(), message);
        assertEquals(ResourceAuthenticationOutcome.REJECTED, result.getOutcome(), message);
    }
}
