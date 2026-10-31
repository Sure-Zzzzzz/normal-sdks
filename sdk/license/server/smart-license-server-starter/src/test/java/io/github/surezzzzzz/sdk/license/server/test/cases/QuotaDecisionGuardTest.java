package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaReservationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.repository.QuotaReservationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.QuotaDecisionGuard;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 在线动作只接受真实主体当前授权中的持久额度事实。 */
@Slf4j
class QuotaDecisionGuardTest {

    private GrantRepository grants;
    private ModelVersionRepository models;
    private QuotaReservationRepository reservations;
    private SubjectAssignmentRepository assignments;
    private LicenseRevocationGuard revocations;
    private QuotaDecisionGuard guard;

    @BeforeEach
    void setup() {
        grants = mock(GrantRepository.class);
        models = mock(ModelVersionRepository.class);
        reservations = mock(QuotaReservationRepository.class);
        assignments = mock(SubjectAssignmentRepository.class);
        revocations = mock(LicenseRevocationGuard.class);
        guard = new QuotaDecisionGuard(grants, models, reservations, assignments, revocations);
        GrantRevisionEntity grant = new GrantRevisionEntity("grant-1", "customer-1", "product-1", "PERSON",
                "ASSIGNED_SUBJECTS", null, "ONLINE", null, "model-1", "{\"limit\":10}");
        when(grants.findById("grant-1")).thenReturn(Optional.of(grant));
        model("CONCURRENT");
    }

    @Test
    void ruleTrueCannotSupplyMissingRequiredQuota() {
        assertThat(satisfied(request(null, null), "user-a", "iam", 1000L)).isFalse();
        when(reservations.findById("reservation-1")).thenReturn(Optional.of(row("CONCURRENT", "user-a", "iam", 1300L)));
        assertThat(satisfied(request("reservation-1", null), "user-a", "iam", 1000L)).isTrue();
    }

    @Test
    void referenceCannotCrossPrincipalAuthenticationSourceOrExpiry() {
        when(reservations.findById("reservation-1")).thenReturn(Optional.of(row("CONCURRENT", "user-a", "iam", 1300L)));
        assertThat(satisfied(request("reservation-1", null), "user-b", "iam", 1000L)).isFalse();
        assertThat(satisfied(request("reservation-1", null), "user-a", "inherited-source", 1000L)).isFalse();
        assertThat(satisfied(request("reservation-1", null), "user-a", "iam", 1300L)).isFalse();
        QuotaReservationEntity released = row("CONCURRENT", "user-a", "iam", 1300L);
        released.release();
        when(reservations.findById("reservation-1")).thenReturn(Optional.of(released));
        assertThat(satisfied(request("reservation-1", null), "user-a", "iam", 1000L)).isFalse();
    }

    @Test
    void cumulativeConsumptionMustMatchTheActualBusinessOperationAndModelKind() {
        model("CONSUMPTION");
        when(reservations.findById("reservation-1")).thenReturn(Optional.of(row("CONSUMPTION", "user-a", "iam", 2000L)));
        assertThat(satisfied(request("reservation-1", "operation-1"), "user-a", "iam", 1000L)).isTrue();
        assertThat(satisfied(request("reservation-1", "another-operation"), "user-a", "iam", 1000L)).isFalse();
        assertThat(satisfied(request("reservation-1", null), "user-a", "iam", 1000L)).isFalse();
        model("CONCURRENT");
        assertThat(satisfied(request("reservation-1", "operation-1"), "user-a", "iam", 1000L)).isFalse();
    }

    @Test
    void namedSeatRequiresCurrentAssignmentMetricAndNoFabricatedReservation() {
        model("NAMED_SEAT");
        assertThat(satisfied(request(null, null), "user-a", "iam", 1000L)).isFalse();
        when(assignments.findByGrantId("grant-1")).thenReturn(Collections.singletonList(new SubjectAssignmentEntity(
                "assignment-1", "grant-1", 1L, "PERSON", "iam", "user-a", "[\"metric-1\"]")));
        assertThat(satisfied(request(null, null), "user-a", "iam", 1000L)).isTrue();
        assertThat(satisfied(request(null, null), "user-b", "iam", 1000L)).isFalse();
    }

    @Test
    void namedSeatFromPreviousGrantRevisionCannotSatisfyCurrentAuthorization() {
        model("NAMED_SEAT");
        when(assignments.findByGrantId("grant-1")).thenReturn(Collections.singletonList(new SubjectAssignmentEntity(
                "assignment-1", "grant-1", 1L, "PERSON", "iam", "user-a", "[\"metric-1\"]")));
        GrantRevisionEntity current = new GrantRevisionEntity("grant-1", "customer-1", "product-1", "PERSON",
                "ASSIGNED_SUBJECTS", null, "ONLINE", null, "model-1", "{\"limit\":10}");
        current.setGrantRevision(2L);
        when(grants.findById("grant-1")).thenReturn(Optional.of(current));

        assertThat(satisfied(request(null, null), "user-a", "iam", 1000L)).isFalse();
    }

    @Test
    void missingModelIsDependencyFailureRatherThanAnAllowOrEmptySuccess() {
        when(models.findById("model-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> satisfied(request(null, null), "user-a", "iam", 1000L))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
    }

    @Test
    void missingGrantFailsClosedBeforeQuotaSnapshotDereference() {
        when(grants.findById("grant-1")).thenReturn(Optional.empty());

        assertThat(satisfied(request("reservation-1", null), "user-a", "iam", 1000L)).isFalse();
        verifyNoInteractions(models, reservations, assignments, revocations);
    }

    private boolean satisfied(OnlineAuthorizationRequest request, String subject, String authenticationSource, long now) {
        return guard.satisfied(request, "iam", subject, authenticationSource, now);
    }

    private OnlineAuthorizationRequest request(String reference, String operation) {
        OnlineAuthorizationRequest request = OnlineAuthorizationRequest.personOrService("product-1", "grant-1",
                SubjectKind.PERSON, "run", null, Collections.emptyMap(), reference == null
                ? Collections.emptyList() : Collections.singletonList(reference));
        return operation == null ? request : OnlineAuthorizationRequest.consumption(request, operation);
    }

    private static QuotaReservationEntity row(String kind, String user, String source, long validUntil) {
        return new QuotaReservationEntity("reservation-1", "grant-1", "metric-1", 1L, kind, "run", 1L,
                "operation-1", "operation-key", 1L, validUntil, "iam:" + user, source, null, null);
    }

    private void model(String kind) {
        ModelVersionEntity model = new ModelVersionEntity("model-1", "model", "product-1", 1L,
                QuotaTestFixture.model(kind, "ONLINE", "CONSUMPTION".equals(kind) ? "TOTAL" : null));
        model.publish();
        when(models.findById("model-1")).thenReturn(Optional.of(model));
    }
}
