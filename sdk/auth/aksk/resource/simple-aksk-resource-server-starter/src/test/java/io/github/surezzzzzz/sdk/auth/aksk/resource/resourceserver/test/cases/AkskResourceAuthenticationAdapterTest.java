package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.AkskResourceIntrospectionClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.AkskResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationOutcome;
import io.github.surezzzzzz.sdk.auth.resource.core.model.BearerResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AKSK资源认证适配器测试。
 *
 * @author surezzzzzz
 */
class AkskResourceAuthenticationAdapterTest {

    private static final ResourceAuthenticationSourceId AKSK_SOURCE =
            new ResourceAuthenticationSourceId(AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID);
    private static final ResourceAuthenticationSourceId IAM_SOURCE = new ResourceAuthenticationSourceId("iam");
    private static final String CLIENT_ID = "service-client";

    @Test
    void shouldAuthenticateBoundServiceAuthorization() {
        OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);
        when(introspector.introspect(anyString())).thenReturn(principal(claims(CLIENT_ID,
                ApplicationAuthorizationSubjectType.SERVICE, CLIENT_ID, null)));
        AkskResourceAuthenticationAdapter adapter = new AkskResourceAuthenticationAdapter(introspector);

        ResourceAuthenticationResult result = adapter.authenticate(credential(AKSK_SOURCE));

        assertEquals(ResourceAuthenticationOutcome.AUTHENTICATED, result.getOutcome());
        assertNotNull(result.getPrincipal());
        assertEquals(CLIENT_ID, result.getPrincipal().getSubjectId());
        assertEquals(ApplicationAuthorizationSubjectType.SERVICE,
                result.getApplicationAuthorization().getSubjectType());
        verify(introspector).introspect(anyString());
    }

    @Test
    void shouldRestoreNestedDataGrantDocument() {
        OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);
        DataGrantDocument document = dataGrantDocument();
        when(introspector.introspect(anyString())).thenReturn(principal(claims(CLIENT_ID,
                ApplicationAuthorizationSubjectType.SERVICE, CLIENT_ID, document)));
        AkskResourceAuthenticationAdapter adapter = new AkskResourceAuthenticationAdapter(introspector);

        ResourceAuthenticationResult result = adapter.authenticate(credential(AKSK_SOURCE));

        assertEquals(ResourceAuthenticationOutcome.AUTHENTICATED, result.getOutcome());
        assertEquals(document, result.getApplicationAuthorization().getDataGrantDocument());
    }

    @Test
    void shouldRejectCredentialFromAnotherSourceWithoutIntrospection() {
        OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);
        AkskResourceAuthenticationAdapter adapter = new AkskResourceAuthenticationAdapter(introspector);

        ResourceAuthenticationResult result = adapter.authenticate(credential(IAM_SOURCE));

        assertRejected(result, ResourceAuthenticationFailureCategory.CREDENTIAL_MALFORMED);
        verify(introspector, never()).introspect(anyString());
    }

    @Test
    void shouldRejectMissingOrInactiveActiveClaim() {
        OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);
        Map<String, Object> missingActive = claims(CLIENT_ID, ApplicationAuthorizationSubjectType.SERVICE, CLIENT_ID, null);
        missingActive.remove(AkskResourceIntrospectionClaimConstant.ACTIVE);
        when(introspector.introspect(anyString())).thenReturn(principal(missingActive));
        AkskResourceAuthenticationAdapter adapter = new AkskResourceAuthenticationAdapter(introspector);

        assertRejected(adapter.authenticate(credential(AKSK_SOURCE)), ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);

        Map<String, Object> inactive = claims(CLIENT_ID, ApplicationAuthorizationSubjectType.SERVICE, CLIENT_ID, null);
        inactive.put(AkskResourceIntrospectionClaimConstant.ACTIVE, Boolean.FALSE);
        when(introspector.introspect(anyString())).thenReturn(principal(inactive));
        assertRejected(adapter.authenticate(credential(AKSK_SOURCE)), ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);
    }

    @Test
    void shouldRejectUnavailableProvider() {
        OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);
        when(introspector.introspect(anyString())).thenThrow(new OAuth2IntrospectionException("unavailable"));
        AkskResourceAuthenticationAdapter adapter = new AkskResourceAuthenticationAdapter(introspector);

        assertRejected(adapter.authenticate(credential(AKSK_SOURCE)),
                ResourceAuthenticationFailureCategory.PROVIDER_UNAVAILABLE);
    }

    @Test
    void shouldRejectInvalidAuthorizationSnapshot() {
        OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);
        Map<String, Object> missingAuthorization = new HashMap<String, Object>();
        missingAuthorization.put(AkskResourceIntrospectionClaimConstant.ACTIVE, Boolean.TRUE);
        missingAuthorization.put(JwtClaimConstant.CLIENT_ID, CLIENT_ID);
        when(introspector.introspect(anyString())).thenReturn(principal(missingAuthorization));
        AkskResourceAuthenticationAdapter adapter = new AkskResourceAuthenticationAdapter(introspector);

        assertRejected(adapter.authenticate(credential(AKSK_SOURCE)),
                ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);

        when(introspector.introspect(anyString())).thenReturn(principal(claims(CLIENT_ID,
                ApplicationAuthorizationSubjectType.HUMAN, CLIENT_ID, null)));
        assertRejected(adapter.authenticate(credential(AKSK_SOURCE)),
                ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);

        when(introspector.introspect(anyString())).thenReturn(principal(claims(CLIENT_ID,
                ApplicationAuthorizationSubjectType.SERVICE, "another-service", null)));
        assertRejected(adapter.authenticate(credential(AKSK_SOURCE)),
                ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID);
    }

    private BearerResourceCredential credential(ResourceAuthenticationSourceId sourceId) {
        return new BearerResourceCredential(sourceId, "test-credential");
    }

    private OAuth2AuthenticatedPrincipal principal(Map<String, Object> attributes) {
        return new DefaultOAuth2AuthenticatedPrincipal(CLIENT_ID, attributes, Collections.emptyList());
    }

    private Map<String, Object> claims(String clientId, ApplicationAuthorizationSubjectType subjectType,
                                       String subjectId, DataGrantDocument dataGrantDocument) {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                subjectType, subjectId, "application-a", true, Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.singletonList("api.read"), dataGrantDocument, 1L,
                "manifest-a", "digest-a", now.minusSeconds(1L), now.plusSeconds(60L));
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put(AkskResourceIntrospectionClaimConstant.ACTIVE, Boolean.TRUE);
        claims.put(JwtClaimConstant.CLIENT_ID, clientId);
        claims.put(JwtClaimConstant.APPLICATION_AUTHORIZATION,
                ApplicationAuthorizationContextClaimMapper.toClaim(authorization));
        return claims;
    }

    private DataGrantDocument dataGrantDocument() {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(new DataGrant("order", Collections.singletonList("read"), false,
                        Arrays.asList(new DataConstraint("tenantId", DataConstraintOperator.IN,
                                        Collections.singletonList("tenant-a")),
                                new DataConstraint("departmentId", DataConstraintOperator.IN,
                                        Collections.singletonList("department-a"))))));
    }

    private void assertRejected(ResourceAuthenticationResult result,
                                ResourceAuthenticationFailureCategory category) {
        assertEquals(ResourceAuthenticationOutcome.REJECTED, result.getOutcome());
        assertEquals(category, result.getFailureCategory());
    }
}
