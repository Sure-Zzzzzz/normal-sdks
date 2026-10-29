package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerResourceOwnerResolverBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resource 协作桥将已验证的人员/服务主体转换为 CRM owner。 */
class SimpleCrmServerResourceOwnerResolverBridgeTest {

    private final SimpleCrmServerResourceOwnerResolverBridge bridge =
            new SimpleCrmServerResourceOwnerResolverBridge();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesHumanResourcePrincipal() {
        authenticate(ResourceSubjectType.HUMAN, "user-1");

        CrmOwner owner = bridge.resolve();
        assertEquals("iam:user-1", owner.getOwner());
        assertTrue(owner.isHuman());
    }

    @Test
    void resolvesServiceResourcePrincipal() {
        authenticate(ResourceSubjectType.SERVICE, "client-1");

        CrmOwner owner = bridge.resolve();
        assertEquals("aksk:client-1", owner.getOwner());
        assertTrue(owner.isService());
    }

    @Test
    void rejectsUnverifiedAuthenticationInsteadOfFallingBackToName() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("plain-user", "credentials"));

        assertThrows(CrmException.class, bridge::resolve,
                "Resource 协作形态不得把普通 Authentication 名称当作已验证 owner");
    }

    private void authenticate(ResourceSubjectType subjectType, String subjectId) {
        ApplicationAuthorizationSubjectType authorizationSubjectType = subjectType == ResourceSubjectType.HUMAN
                ? ApplicationAuthorizationSubjectType.HUMAN : ApplicationAuthorizationSubjectType.SERVICE;
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId("test"), subjectType, subjectId);
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                authorizationSubjectType, subjectId, "crm-test", true,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList(), null, 1L, "1", "digest",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"));
        VerifiedResourceContext verified = new VerifiedResourceContext(principal, authorization, "request-1");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(verified, "credentials"));
    }
}
