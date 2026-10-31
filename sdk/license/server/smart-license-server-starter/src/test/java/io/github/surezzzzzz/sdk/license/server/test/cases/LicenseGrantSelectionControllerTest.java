package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseGrantSelectionController;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 候选发现按真实身份、对象、商业阶段与完整分页求交，不以首批或管理全量权限冒充资格。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseGrantSelectionControllerTest {

    private GrantRepository grants;
    private ModelVersionRepository models;
    private SubjectAdmissionService admission;
    private LicenseRevocationGuard guard;
    private LicenseGrantSelectionController controller;
    private ModelVersionEntity model;

    @BeforeEach
    void setUp() throws Exception {
        grants = mock(GrantRepository.class);
        models = mock(ModelVersionRepository.class);
        admission = mock(SubjectAdmissionService.class);
        guard = mock(LicenseRevocationGuard.class);
        controller = new LicenseGrantSelectionController(grants, models, admission, guard);
        model = new ModelVersionEntity("mv-1", "model-1", "prod-1", 7L, snapshot());
        model.publish();
        when(models.findById("mv-1")).thenReturn(Optional.of(model));
        when(admission.isAdmitted(anyString(), anyString())).thenReturn(SubjectAdmissionService.Admission.ADMITTED);
        authenticate(false, true);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void actionsAreAnObjectAndRetiredReferencedModelsRemainAvailable() throws Exception {
        batch(grant("gr-1", "PERSON"));
        model.retire();
        JsonNode result = select("PERSON", "", TestPlans.all("use"));
        assertThat(result.path("total").longValue()).isEqualTo(1L);
        assertThat(result.path("items").get(0).path("modelVersion").longValue()).isEqualTo(7L);
        verify(guard).onlineRejection(any(), eq("iam"), eq("subject-test"));
    }

    @Test
    void fullSecondBatchContributesToTotalAndRequestedPage() throws Exception {
        List<GrantRevisionEntity> rows = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            rows.add(grant(String.format("gr-%03d", i), "PERSON"));
        }
        when(grants.findSelectionBatch(eq("prod-1"), eq("PERSON"), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(rows.subList(0, 200)));
        when(grants.findSelectionBatch(eq("prod-1"), eq("PERSON"), isNull(), eq("gr-199"), any()))
                .thenReturn(new PageImpl<>(rows.subList(200, 201)));
        JsonNode result = select("PERSON", ",\"page\":3,\"size\":100", TestPlans.all("use"));
        assertThat(result.path("total").longValue()).isEqualTo(201L);
        assertThat(result.path("totalPages").longValue()).isEqualTo(3L);
        assertThat(result.path("items").size()).isEqualTo(1);
        assertThat(result.path("items").get(0).path("grantId").textValue()).isEqualTo("gr-200");
        log.info("候选扫描跨过200条首批，末页与total均来自完整过滤结果");
    }

    @Test
    void maximalPageDoesNotOverflowIntoTheFirstPage() throws Exception {
        batch(grant("gr-1", "PERSON"));
        JsonNode result = select("PERSON", ",\"page\":2147483647,\"size\":100", TestPlans.all("use"));
        assertThat(result.path("total").longValue()).isEqualTo(1L);
        assertThat(result.path("items").size()).isZero();
    }

    @Test
    void restrictedDataReachesTheSqlQueryAndDeniedDataDoesNotQuery() throws Exception {
        when(grants.findSelectionBatch(eq("prod-1"), eq("PERSON"), eq(Collections.singleton("gr-1")),
                isNull(), any())).thenReturn(new PageImpl<>(Collections.singletonList(grant("gr-1", "PERSON"))));
        assertThat(select("PERSON", "", TestPlans.restricted("use", "gr-1")).path("total").longValue()).isEqualTo(1L);
        clearInvocations(grants);
        status(() -> select("PERSON", "", TestPlans.deny()), 403);
        verifyNoInteractions(grants);
    }

    @Test
    void serviceRequiresActualAssignmentAndUsesTheVerifiedSource() throws Exception {
        authenticate(true, true);
        useModel("SERVICE");
        batch(grant("gr-1", "SERVICE"));
        when(guard.onlineRejection(any(), eq("source-test"), eq("subject-test")))
                .thenReturn(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        assertThat(select("SERVICE", "", TestPlans.all("use")).path("total").longValue()).isZero();
        verify(guard).onlineRejection(any(), eq("source-test"), eq("subject-test"));
    }

    @Test
    void deploymentRequiresActivationAndItsCallerBinding() throws Exception {
        authenticate(true, true);
        useModel("DEPLOYMENT");
        status(() -> select("DEPLOYMENT", "", TestPlans.all("use")), 422);
        verifyNoInteractions(grants);
        batch(grant("gr-1", "DEPLOYMENT"));
        when(guard.onlineRejection(argThat(value -> "act-1".equals(value.getActivationId())),
                eq("source-test"), eq("subject-test"))).thenReturn(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        assertThat(select("DEPLOYMENT", ",\"activationId\":\"act-1\"", TestPlans.all("use"))
                .path("total").longValue()).isZero();
    }

    @Test
    void unverifiedProxyAndCrossKindIdentityAreRejectedBeforeQuery() {
        status(() -> select("PERSON", ",\"subjectEvidence\":{}", TestPlans.all("use")), 422);
        status(() -> select("SERVICE", "", TestPlans.all("use")), 403);
        authenticate(true, true);
        status(() -> select("PERSON", "", TestPlans.all("use")), 403);
        verifyNoInteractions(grants);
    }

    @Test
    void missingApiPermissionDoesNotBecomeUsableBecauseDataIsAll() {
        authenticate(false, false);
        status(() -> select("PERSON", "", TestPlans.all("use")), 403);
        verifyNoInteractions(grants);
    }

    @Test
    void nonMemberAndUnavailableDirectoryHaveDifferentOutcomes() throws Exception {
        batch(grant("gr-1", "PERSON"));
        when(admission.isAdmitted("cu-1", "subject-test")).thenReturn(SubjectAdmissionService.Admission.NOT_BOUND);
        assertThat(select("PERSON", "", TestPlans.all("use")).path("total").longValue()).isZero();
        when(admission.isAdmitted("cu-1", "subject-test")).thenReturn(SubjectAdmissionService.Admission.UNAVAILABLE);
        status(() -> select("PERSON", "", TestPlans.all("use")), 503);
    }

    @Test
    void commercialPhaseFiltersNotStartedEndedAndGraceOutsideDeclaredActions() throws Exception {
        long now = Instant.now().getEpochSecond();
        GrantRevisionEntity future = grant("gr-1", "PERSON").withValidity("FIXED", now + 100L, now + 200L, now + 300L);
        GrantRevisionEntity ended = grant("gr-2", "PERSON").withValidity("FIXED", now - 300L, now - 200L, now - 100L);
        GrantRevisionEntity grace = grant("gr-3", "PERSON").withValidity("FIXED", now - 100L, now - 10L, now + 100L);
        batch(future, ended, grace);
        JsonNode result = select("PERSON", "", TestPlans.all("use"));
        assertThat(result.path("total").longValue()).isEqualTo(1L);
        assertThat(result.path("items").get(0).path("commercialPhase").textValue()).isEqualTo("COMMERCIAL_GRACE");
        model = new ModelVersionEntity("mv-1", "model-1", "prod-1", 7L, snapshot().replace("\"graceActions\":[\"read\"]", "\"graceActions\":[]"));
        model.publish();
        when(models.findById("mv-1")).thenReturn(Optional.of(model));
        assertThat(select("PERSON", "", TestPlans.all("use")).path("total").longValue()).isZero();
    }

    @Test
    void invalidStoredModelAndValidityReportDependencyFailure() {
        batch(grant("gr-1", "PERSON"));
        when(models.findById("mv-1")).thenReturn(Optional.of(new ModelVersionEntity("mv-1", "model-1", "prod-1", 7L, "{}")));
        status(() -> select("PERSON", "", TestPlans.all("use")), 503);
        when(models.findById("mv-1")).thenReturn(Optional.of(model));
        batch(grant("gr-1", "PERSON").withValidity(null, null, null, null));
        status(() -> select("PERSON", "", TestPlans.all("use")), 503);
    }

    @Test
    void invalidPagingAndForeignActivationAreUnprocessable() {
        for (String extra : Arrays.asList(",\"page\":0", ",\"page\":1.5", ",\"size\":101", ",\"activationId\":\"act-1\"")) {
            status(() -> select("PERSON", extra, TestPlans.all("use")), 422);
        }
        verifyNoInteractions(grants);
    }

    private GrantRevisionEntity grant(String id, String kind) {
        GrantRevisionEntity row = new GrantRevisionEntity(id, "cu-1", "prod-1", kind,
                "PERSON".equals(kind) ? "CUSTOMER_MEMBERS" : "SERVICE".equals(kind) ? "ASSIGNED_SUBJECTS" : "ALLOCATED_DEPLOYMENTS",
                null, "ONLINE", null, "mv-1", "{}");
        row.setState("ENABLED");
        return row;
    }

    private void batch(GrantRevisionEntity... rows) {
        when(grants.findSelectionBatch(anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(Arrays.asList(rows)));
    }

    private JsonNode select(String kind, String extra, io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan plan) throws Exception {
        String body = "{\"productId\":\"prod-1\",\"subjectKind\":\"" + kind + "\",\"action\":\"read\"" + extra + "}";
        return StrictJson.mapper().readTree(controller.select(body, plan, new MockHttpServletRequest()).getBody());
    }

    private void authenticate(boolean service, boolean canEvaluate) {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext("simple-application-authorization", "1.0",
                service ? ApplicationAuthorizationSubjectType.SERVICE : ApplicationAuthorizationSubjectType.HUMAN,
                "subject-test", "license", true, Collections.emptyList(), Collections.emptyList(),
                canEvaluate ? Collections.singletonList("license.decision.evaluate") : Collections.emptyList(),
                VerifiedContextFactory.create(true).getApplicationAuthorization().getDataGrantDocument(),
                1L, "1", "digest", now, now.plusSeconds(3600));
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(new ResourceAuthenticationSourceId(service ? "source-test" : "iam"),
                service ? ResourceSubjectType.SERVICE : ResourceSubjectType.HUMAN, "subject-test");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new VerifiedResourceContext(principal, authorization, "req-selection-test"), null));
    }

    private static String snapshot() throws Exception {
        return snapshot("PERSON");
    }

    private void useModel(String kind) throws Exception {
        model = new ModelVersionEntity("mv-1", "model-1", "prod-1", 7L, snapshot(kind));
        model.publish();
        when(models.findById("mv-1")).thenReturn(Optional.of(model));
    }

    private static String snapshot(String kind) throws Exception {
        String eligibility = "PERSON".equals(kind) ? "CUSTOMER_MEMBERS"
                : "SERVICE".equals(kind) ? "ASSIGNED_SUBJECTS" : "ALLOCATED_DEPLOYMENTS";
        String organization = "PERSON".equals(kind)
                ? "{\"required\":true,\"maxObservationAgeSeconds\":60,\"membershipLeaseSeconds\":60}"
                : "{\"required\":false}";
        String json = "{\"fields\":{\"rights\":{},\"facts\":{}},\"actions\":{\"read\":{\"bool\":true}},"
                + "\"subjectPolicy\":{\"allowedKinds\":[\"" + kind + "\"],\"allowedEligibilityKinds\":[\"" + eligibility + "\"],\"offlineProofProfiles\":[]},"
                + "\"validityPolicy\":{\"allowedKinds\":[\"FIXED\"],\"minimumStartsAt\":1,\"maximumStartsAt\":253402300799,\"minimumDurationSeconds\":1,\"maximumDurationSeconds\":86400,\"maximumGraceSeconds\":86400,\"graceActions\":[\"read\"]},"
                + "\"connectivityPolicy\":{\"allowedModes\":[\"ONLINE\"]},\"clockPolicy\":{\"skewSeconds\":0,\"rollbackToleranceSeconds\":0},"
                + "\"devicePolicy\":{\"mode\":\"NONE\"},\"organizationPolicy\":" + organization + ",\"quotaDefinitions\":{},\"quotaConstraints\":{},"
                + "\"revocationPolicy\":{\"mode\":\"OPTIONAL\"},\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\",\"organization-lease/1\"]}";
        return ModelPolicyWire.validate(StrictJson.mapper().readTree(json), "model-1", 7L, null);
    }

    private void status(CheckedAction action, int expected) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(value -> ((LicenseHttpException) value).getStatus()).isEqualTo(expected);
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
