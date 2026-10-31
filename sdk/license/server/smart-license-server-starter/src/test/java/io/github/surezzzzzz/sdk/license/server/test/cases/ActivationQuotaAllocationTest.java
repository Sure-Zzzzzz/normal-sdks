package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseSubjectController;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationQuotaAllocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationQuotaAllocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

/**
 * 部署激活 LOCAL 额度台账的边界：完整覆盖、授权限额、固定窗口锚与失败关闭。
 *
 * @author surezzzzzz
 */
class ActivationQuotaAllocationTest {

    private static final String GRANT_ID = "gr-local";
    private final ActivationRepository activations = Mockito.mock(ActivationRepository.class);
    private final ActivationQuotaAllocationRepository allocations =
            Mockito.mock(ActivationQuotaAllocationRepository.class);
    private final GrantRepository grants = Mockito.mock(GrantRepository.class);
    private final ModelVersionRepository models = Mockito.mock(ModelVersionRepository.class);
    private LicenseSubjectController controller;
    private GrantRevisionEntity grant;

    @BeforeEach
    void setUp() {
        grant = new GrantRevisionEntity(GRANT_ID, "customer-example", "product-example",
                "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", "order-example",
                "mv-local", "{\"channelLimit\":5}")
                .withValidity("FIXED", 1234L, 9999L, 10000L);
        when(grants.findById(GRANT_ID)).thenReturn(Optional.of(grant));
        when(grants.findWithLockByGrantId(GRANT_ID)).thenReturn(Optional.of(grant));
        when(activations.findByGrantId(GRANT_ID)).thenReturn(Collections.<ActivationEntity>emptyList());
        when(activations.saveAndFlush(any(ActivationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(allocations.findByActivationIdOrderByMetricAsc(anyString()))
                .thenReturn(Collections.<ActivationQuotaAllocationEntity>emptyList());
        when(allocations.saveAll(any())).thenAnswer(invocation -> {
            List<ActivationQuotaAllocationEntity> values = new ArrayList<ActivationQuotaAllocationEntity>();
            for (ActivationQuotaAllocationEntity value : (Iterable<ActivationQuotaAllocationEntity>) invocation
                    .getArgument(0)) {
                values.add(value);
            }
            return values;
        });
        when(models.findById("mv-local")).thenReturn(Optional.of(publishedModel()));
        controller = new LicenseSubjectController(Mockito.mock(SubjectAssignmentRepository.class),
                activations, grants, allocations, models);
    }

    @Test
    void publishedModelRequiresEveryLocalMetricAndPersistsFixedWindowAnchor() {
        ResponseEntity<String> response = controller.createActivation(GRANT_ID,
                body("{\"metric\":\"localSeats\",\"quantity\":2,\"retention\":\"FIXED\","
                        + "\"retainUntil\":9000}"),
                "activation-quota-1", TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated());

        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(allocations).saveAll(captor.capture());
        ActivationQuotaAllocationEntity saved = (ActivationQuotaAllocationEntity) captor.getValue().iterator().next();
        assertThat(saved.getMetric()).isEqualTo("localSeats");
        assertThat(saved.getQuantity()).isEqualTo(2L);
        assertThat(saved.getGrantRevision()).isEqualTo(grant.getGrantRevision());
        assertThat(saved.getWindowAnchorAt()).isEqualTo(1234L);
    }

    @Test
    void missingMetricIsRejectedBeforeAnyAllocationIsWritten() {
        assertStatus(() -> controller.createActivation(GRANT_ID,
                "{\"deploymentCode\":\"deployment-1\",\"requestedAllocations\":[]}",
                "activation-quota-2", TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()), 422);
        Mockito.verify(allocations, Mockito.never()).saveAll(any());
    }

    @Test
    void duplicateMetricAndOverLimitAreRejected() {
        assertStatus(() -> controller.createActivation(GRANT_ID,
                body("{\"metric\":\"localSeats\",\"quantity\":1,\"retention\":\"PERPETUAL\"},"
                        + "{\"metric\":\"localSeats\",\"quantity\":1,\"retention\":\"PERPETUAL\"}"),
                "activation-quota-3", TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()), 422);
        assertStatus(() -> controller.createActivation(GRANT_ID,
                body("{\"metric\":\"localSeats\",\"quantity\":6,\"retention\":\"PERPETUAL\"}"),
                "activation-quota-4", TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()), 409);
    }

    @Test
    void draftModelCannotSupplyLocalAllocation() {
        ModelVersionEntity draft = new ModelVersionEntity("mv-local", "model-local", "product-example", 1,
                localSnapshot());
        when(models.findById("mv-local")).thenReturn(Optional.of(draft));
        assertStatus(() -> controller.createActivation(GRANT_ID,
                body("{\"metric\":\"localSeats\",\"quantity\":1,\"retention\":\"PERPETUAL\"}"),
                "activation-quota-5", TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()), 422);
    }

    @Test
    void historicalTotalOverflowFailsClosed() {
        ActivationEntity existing = new ActivationEntity("act-existing", GRANT_ID, "deployment-old", "{}");
        ActivationQuotaAllocationEntity exhausted = new ActivationQuotaAllocationEntity(
                "alloc-existing", GRANT_ID, existing.getActivationId(), grant.getGrantRevision(),
                "localSeats", Long.MAX_VALUE, "PERPETUAL", null, 1234L);
        when(activations.findByGrantId(GRANT_ID)).thenReturn(Collections.singletonList(existing));
        when(allocations.findByActivationIdOrderByMetricAsc("act-existing"))
                .thenReturn(Collections.singletonList(exhausted));
        assertStatus(() -> controller.createActivation(GRANT_ID,
                body("{\"metric\":\"localSeats\",\"quantity\":1,\"retention\":\"PERPETUAL\"}"),
                "activation-quota-6", TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()), 409);
    }

    private static ModelVersionEntity publishedModel() {
        ModelVersionEntity model = new ModelVersionEntity("mv-local", "model-local", "product-example", 1,
                localSnapshot());
        model.publish();
        return model;
    }

    private static String localSnapshot() {
        return LicenseTestModels.publishedSnapshot()
                .replace("\"quotaDefinitions\":{},\"quotaConstraints\":{}",
                        "\"quotaDefinitions\":{\"localSeats\":{\"limitRight\":\"channelLimit\","
                                + "\"kind\":\"CONSUMPTION\",\"enforcement\":\"LOCAL\","
                                + "\"window\":\"FIXED_WINDOW\",\"periodSeconds\":3600}},"
                                + "\"quotaConstraints\":{}")
                .replace("\"clock-state/1\"]", "\"clock-state/1\",\"quota-local/1\"]");
    }

    private static String body(String allocations) {
        return "{\"deploymentCode\":\"deployment-1\",\"requestedAllocations\":[" + allocations + "]}";
    }

    private static void assertStatus(Runnable action, int expected) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus())
                .isEqualTo(expected);
    }
}
