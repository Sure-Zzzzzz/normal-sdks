package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseQuotaController;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaReservationEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.service.JpaQuotaReservationService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** DATA及认证归属在余额和预占状态修改之前执行。 */
@Slf4j
class LicenseQuotaControllerTest {

    private GrantRepository grants;
    private JpaQuotaReservationService reservations;
    private LicenseQuotaController controller;

    @BeforeEach
    void setup() {
        grants = mock(GrantRepository.class);
        reservations = mock(JpaQuotaReservationService.class);
        controller = new LicenseQuotaController(grants, reservations);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniedCreateCannotReadGrantOrMutateLedger() {
        assertStatus(() -> controller.create(body(), TestPlans.deny(), human(true)), 403);
        verifyNoInteractions(grants, reservations);
    }

    @Test
    void releaseAndRenewalCannotWriteOutsideCurrentDataScope() {
        QuotaReservationEntity row = new QuotaReservationEntity("reservation-1", "grant-1", "metric-1", 1L,
                "CONCURRENT", "run", 1L, "operation-1", "operation-key", 1L, 1300L,
                "iam:zhangshuo", "iam", null, null);
        when(reservations.find("reservation-1")).thenReturn(Optional.of(row));
        assertStatus(() -> controller.release("reservation-1", TestPlans.restricted("use", "another-grant"), human(true)), 404);
        assertStatus(() -> controller.renew("reservation-1", "{\"expectedFence\":1}", TestPlans.deny(), human(true)), 403);
        verify(reservations, never()).release(any(), any(), any());
        verify(reservations, never()).renew(any(), anyLong(), any(), any(), any());
    }

    @Test
    void apiPermissionIsRequiredIndependentlyOfDataAll() {
        assertStatus(() -> controller.create(body(), TestPlans.all("use"), human(false)), 403);
        verifyNoInteractions(grants, reservations);
    }

    @Test
    void malformedRevisionAndSubjectKindFailBeforeAnyGrantReadOrWrite() {
        assertStatus(() -> controller.create(body().replace("\"expectedGrantRevision\":1",
                "\"expectedGrantRevision\":0"), TestPlans.all("use"), human(true)), 422);
        assertStatus(() -> controller.create(body().replace("\"PERSON\"", "\"UNKNOWN\""),
                TestPlans.all("use"), human(true)), 422);
        verifyNoInteractions(grants, reservations);
    }

    @Test
    void authenticatedPrincipalAndSourceComeFromOfficialContext() {
        when(grants.findById("grant-1")).thenReturn(Optional.of(new GrantRevisionEntity("grant-1", "customer-1",
                "product-1", "PERSON", "ASSIGNED_SUBJECTS", null, "ONLINE", null)));
        when(reservations.reserve(any())).thenThrow(new JpaQuotaReservationService.QuotaUnavailableException("INSUFFICIENT"));
        assertStatus(() -> controller.create(body(), TestPlans.all("use"), human(true)), 409);
        verify(reservations).reserve(argThat(command -> "iam:zhangshuo".equals(command.getPrincipalId())
                && "iam".equals(command.getAuthenticationSourceId()) && "HUMAN".equals(command.getSubjectType())
                && "run".equals(command.getAction()) && command.getExpectedGrantRevision() == 1L));
    }

    @Test
    void listReadsMetadataInsideSqlDataScope() {
        when(reservations.list(isNull(), isNull(), eq("metric-1"), eq("CONCURRENT"), eq("ACTIVE"),
                argThat((Set<String> ids) -> ids.contains("grant-1")), any()))
                .thenReturn(Page.empty());
        org.springframework.http.ResponseEntity<String> response = controller.list(
                TestPlans.restricted("read", "grant-1"),
                humanWithPermissions(true, "license.quota.read"), 1, 20, null, null, "metric-1",
                "CONCURRENT", "ACTIVE");
        org.assertj.core.api.Assertions.assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        verify(reservations).list(isNull(), isNull(), eq("metric-1"), eq("CONCURRENT"), eq("ACTIVE"),
                argThat(ids -> ids.contains("grant-1")), any());
    }

    @Test
    void listRejectsInvalidPagingAndFilterBeforeSql() {
        assertStatus(() -> controller.list(TestPlans.all("read"),
                humanWithPermissions(true, "license.quota.read"), 0, 20, null, null, null,
                null, null), 422);
        assertStatus(() -> controller.list(TestPlans.all("read"),
                humanWithPermissions(true, "license.quota.read"), 1, 20, null, null, null,
                "UNKNOWN", null), 422);
        verifyNoInteractions(reservations);
    }

    @Test
    void createHidesForeignAndMissingGrantBeforeLedgerMutation() {
        String request = body();
        assertStatus(() -> controller.create(request,
                TestPlans.restricted("use", "another-grant"), human(true)), 404);
        verifyNoInteractions(grants, reservations);
        assertStatus(() -> controller.create(request,
                TestPlans.all("use"), human(true)), 404);
        verify(grants).findById("grant-1");
        verifyNoInteractions(reservations);
    }

    @Test
    void singleReadRejectsGrantOutsideDataScopeBeforeReturningMetadata() {
        QuotaReservationEntity row = new QuotaReservationEntity("reservation-2", "grant-1", "metric-1", 1L,
                "CONCURRENT", "run", 1L, "operation-2", "operation-key-2", 1L, 1300L,
                "iam:zhangshuo", "iam", null, null);
        when(reservations.findOwned("reservation-2", "iam:zhangshuo", "iam")).thenReturn(row);
        assertStatus(() -> controller.get("reservation-2", TestPlans.restricted("use", "grant-2"),
                human(true)), 404);
    }

    @Test
    void singleReadRequiresTheOriginalAuthenticatedOwner() {
        when(reservations.findOwned("reservation-3", "iam:zhangshuo", "iam"))
                .thenThrow(new SecurityException("not-owner"));
        assertStatus(() -> controller.get("reservation-3", TestPlans.all("use"), human(true)), 404);
    }

    @Test
    void singleReadResponseDoesNotExposeAuthenticationFields() {
        QuotaReservationEntity row = new QuotaReservationEntity("reservation-4", "grant-1", "metric-1", 1L,
                "CONCURRENT", "run", 1L, "operation-4", "operation-key-4", 1L, 1300L,
                "iam:zhangshuo", "iam", null, null);
        when(reservations.findOwned("reservation-4", "iam:zhangshuo", "iam")).thenReturn(row);
        org.springframework.http.ResponseEntity<String> response = controller.get(
                "reservation-4", TestPlans.all("use"), human(true));
        assertThat(response.getBody()).doesNotContain("requesterSubject")
                .doesNotContain("authenticationSource");
    }

    private static String body() {
        return "{\"productId\":\"product-1\",\"grantId\":\"grant-1\",\"subjectKind\":\"PERSON\",\"action\":\"run\","
                + "\"metric\":\"metric-1\",\"quantity\":1,\"kind\":\"CONCURRENT\",\"businessOperationId\":\"operation-1\","
                + "\"expectedGrantRevision\":1}";
    }

    private static HttpServletRequest human(boolean permitted) {
        return humanWithPermissions(permitted, "license.quota.use");
    }

    private static HttpServletRequest humanWithPermissions(boolean permitted, String... permissions) {
        VerifiedResourceContext baseline = VerifiedContextFactory.create(true);
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                "simple-application-authorization", "1.0", ApplicationAuthorizationSubjectType.HUMAN,
                "zhangshuo", "license", true, Collections.emptyList(), Collections.emptyList(),
                permitted ? java.util.Arrays.asList(permissions) : Collections.emptyList(),
                baseline.getApplicationAuthorization().getDataGrantDocument(), 1L, "1", "digest",
                Instant.now(), Instant.now().plusSeconds(3600));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new VerifiedResourceContext(baseline.getPrincipal(), authorization, "req-quota-test"), null));
        return new MockHttpServletRequest();
    }

    private static void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(status);
    }
}
