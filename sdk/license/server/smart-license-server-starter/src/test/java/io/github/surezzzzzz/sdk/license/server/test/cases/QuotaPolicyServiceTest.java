package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.QuotaPolicyService;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 额度写前真实资格、商业期限和不可变模型政策回归。 */
@Slf4j
class QuotaPolicyServiceTest {

    private GrantRepository grants;
    private ModelVersionRepository models;
    private LicenseRevocationGuard guard;
    private SubjectAdmissionService admission;
    private QuotaPolicyService policies;
    private GrantRevisionEntity grant;

    @BeforeEach
    void setup() {
        grants = mock(GrantRepository.class);
        models = mock(ModelVersionRepository.class);
        guard = mock(LicenseRevocationGuard.class);
        admission = mock(SubjectAdmissionService.class);
        policies = new QuotaPolicyService(grants, models, guard, admission);
        grant = new GrantRevisionEntity("grant-1", "customer-1", "product-1", "PERSON",
                "ASSIGNED_SUBJECTS", null, "ONLINE", null, "model-1", "{\"limit\":10}");
        grant.withValidity("FIXED", 950L, 2000L, 2100L);
        grant.setState("ENABLED");
        when(grants.findById("grant-1")).thenAnswer(call -> Optional.of(grant));
        model("CONCURRENT", "ONLINE", null);
    }

    @Test
    void reservationCutoffComesFromModelAndNeverExceedsCommercialDeadline() {
        assertThat(require("CONCURRENT", "run", 1000L).getValidUntil()).isEqualTo(1300L);
        grant.withValidity("FIXED", 950L, 1100L, 2100L);
        assertThat(require("CONCURRENT", "run", 1000L).getValidUntil()).isEqualTo(1100L);
        assertThat(require("CONCURRENT", "run", 1000L).getLimit()).isEqualTo(10L);
    }

    @Test
    void notStartedGraceSuspendedOfflineOrRevokedGrantCannotReserve() {
        assertStatus(() -> require("CONCURRENT", "run", 949L), 409);
        assertStatus(() -> require("CONCURRENT", "run", 2000L), 409);
        grant.setState("SUSPENDED");
        assertStatus(() -> require("CONCURRENT", "run", 1000L), 409);
        grant.setState("ENABLED");
        when(guard.onlineRejection(any(), any(), any())).thenReturn(Decision.ReasonCategory.REVOKED);
        assertStatus(() -> require("CONCURRENT", "run", 1000L), 403);
    }

    @Test
    void metricKindEnforcementAndActionMustMatchPublishedModel() {
        assertStatus(() -> require("CONSUMPTION", "run", 1000L), 422);
        assertStatus(() -> require("CONCURRENT", "read", 1000L), 422);
        model("CONCURRENT", "LOCAL", null);
        assertStatus(() -> require("CONCURRENT", "run", 1000L), 422);
        model("NAMED_SEAT", "ONLINE", null);
        assertStatus(() -> require("NAMED_SEAT", "run", 1000L), 422);
    }

    @Test
    void inheritedHumanAuthenticationKeepsIamBusinessSubjectWhileServiceCannotImpersonatePerson() {
        policies.require("product-1", "PERSON", "run", 1L, "grant-1", "metric-1", "CONCURRENT",
                "iam:user-a", "inherited-source", "HUMAN", null, null, 1000L);
        verify(guard).onlineRejection(any(OnlineAuthorizationRequest.class), eq("iam"), eq("user-a"));
        assertStatus(() -> policies.require("product-1", "PERSON", "run", 1L, "grant-1", "metric-1",
                "CONCURRENT", "aksk:machine", "aksk", "SERVICE", null, null, 1000L), 403);
    }

    @Test
    void productSubjectAndCommercialRevisionCannotBeSubstituted() {
        assertStatus(() -> policies.require("other-product", "PERSON", "run", 1L, "grant-1", "metric-1",
                "CONCURRENT", "iam:user-a", "iam", "HUMAN", null, null, 1000L), 409);
        assertStatus(() -> policies.require("product-1", "SERVICE", "run", 1L, "grant-1", "metric-1",
                "CONCURRENT", "iam:user-a", "iam", "HUMAN", null, null, 1000L), 409);
        assertStatus(() -> policies.require("product-1", "PERSON", "run", 2L, "grant-1", "metric-1",
                "CONCURRENT", "iam:user-a", "iam", "HUMAN", null, null, 1000L), 409);
    }

    @Test
    void unboundCustomerIsNotAProofOfMembershipAndDirectoryFailureIs503() {
        grant = new GrantRevisionEntity("grant-1", "customer-1", "product-1", "PERSON",
                "CUSTOMER_MEMBERS", null, "ONLINE", null, "model-1", "{\"limit\":10}");
        grant.withValidity("FIXED", 950L, 2000L, 2100L);
        grant.setState("ENABLED");
        when(admission.isAdmitted("customer-1", "user-a")).thenReturn(SubjectAdmissionService.Admission.NOT_BOUND);
        assertStatus(() -> require("CONCURRENT", "run", 1000L), 403);
        when(admission.isAdmitted("customer-1", "user-a")).thenReturn(SubjectAdmissionService.Admission.UNAVAILABLE);
        assertStatus(() -> require("CONCURRENT", "run", 1000L), 503);
    }

    @Test
    void fixedWindowPolicyReturnsPeriodAndFirstIntroductionAnchor() {
        model("CONSUMPTION", "ONLINE", "FIXED_WINDOW");
        QuotaPolicyService.Policy policy = require("CONSUMPTION", "run", 1000L);
        assertThat(policy.getPeriodSeconds()).isEqualTo(100L);
        assertThat(policy.getInitialAnchorAt()).isEqualTo(950L);
    }

    @Test
    void missingOrBrokenPersistedModelIs503RatherThanBusinessDenial() {
        when(models.findById("model-1")).thenReturn(Optional.empty());
        assertStatus(() -> require("CONCURRENT", "run", 1000L), 503);
        ModelVersionEntity broken = new ModelVersionEntity("model-1", "model", "product-1", 1L, "{}");
        broken.publish();
        when(models.findById("model-1")).thenReturn(Optional.of(broken));
        assertStatus(() -> require("CONCURRENT", "run", 1000L), 503);
    }

    private QuotaPolicyService.Policy require(String kind, String action, long now) {
        return policies.require("product-1", "PERSON", action, 1L, "grant-1", "metric-1", kind,
                "iam:user-a", "iam", "HUMAN", null, null, now);
    }

    private void model(String kind, String enforcement, String window) {
        ModelVersionEntity model = new ModelVersionEntity("model-1", "model", "product-1", 1L,
                QuotaTestFixture.model(kind, enforcement, window));
        model.publish();
        when(models.findById("model-1")).thenReturn(Optional.of(model));
    }

    private static void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(status);
    }
}
