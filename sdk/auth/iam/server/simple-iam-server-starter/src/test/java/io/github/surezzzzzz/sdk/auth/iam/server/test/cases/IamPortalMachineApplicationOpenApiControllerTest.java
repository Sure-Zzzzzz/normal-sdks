package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.iam.server.controller.rest.IamPortalMachineApplicationOpenApiController;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMachineApplicationPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalMachineApplicationDirectoryService;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class IamPortalMachineApplicationOpenApiControllerTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsHumanSubjectEvenWhenTheDirectoryServiceWouldAllowIt() {
        IamPortalMachineApplicationDirectoryService service = mock(IamPortalMachineApplicationDirectoryService.class);
        IamPortalMachineApplicationOpenApiController controller =
                new IamPortalMachineApplicationOpenApiController(service);
        authenticate(ResourceSubjectType.HUMAN);

        org.springframework.http.ResponseEntity<?> response =
                controller.listApplications(0, 20, null, DataAccessPlan.deny());

        assertEquals(403, response.getStatusCodeValue());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertNotNull(response.getBody());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidPaginationAndRevisionWithoutClamping() {
        IamPortalMachineApplicationDirectoryService service = mock(IamPortalMachineApplicationDirectoryService.class);
        IamPortalMachineApplicationOpenApiController controller =
                new IamPortalMachineApplicationOpenApiController(service);
        authenticate(ResourceSubjectType.SERVICE);

        org.springframework.http.ResponseEntity<?> response =
                controller.listApplications(-1, 20, 1L, DataAccessPlan.deny());

        assertEquals(400, response.getStatusCodeValue());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        verifyNoInteractions(service);
    }

    @Test
    void successfulServiceReadIsNotCacheable() {
        IamPortalMachineApplicationDirectoryService service = mock(IamPortalMachineApplicationDirectoryService.class);
        IamPortalMachineApplicationOpenApiController controller =
                new IamPortalMachineApplicationOpenApiController(service);
        authenticate(ResourceSubjectType.SERVICE);
        PortalMachineApplicationPageResponse page = PortalMachineApplicationPageResponse.builder()
                .catalogRevision(4L).totalElements(0).page(0).size(20)
                .items(java.util.Collections.emptyList()).build();
        when(service.read(0, 20, 4L, null))
                .thenReturn(IamPortalMachineApplicationDirectoryService.ReadResult.success(page));

        org.springframework.http.ResponseEntity<?> response =
                controller.listApplications(0, 20, 4L, null);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    }

    private void authenticate(ResourceSubjectType subjectType) {
        String subjectId = "portal-gateway";
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                subjectType == ResourceSubjectType.SERVICE
                        ? ApplicationAuthorizationSubjectType.SERVICE : ApplicationAuthorizationSubjectType.HUMAN,
                subjectId, "portal-gateway", true, Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(), null, 1L,
                "manifest", "digest", now.minusSeconds(1L), now.plusSeconds(60L));
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId("portal-gateway"), subjectType, subjectId);
        VerifiedResourceContext context = new VerifiedResourceContext(principal, authorization, "request-id");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(context, "unused", "ROLE_RESOURCE"));
    }
}
