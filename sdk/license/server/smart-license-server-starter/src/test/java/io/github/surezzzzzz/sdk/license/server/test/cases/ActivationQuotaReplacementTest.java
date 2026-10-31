package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseSubjectController;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationQuotaAllocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationQuotaAllocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 换机必须保留额度事实，但不能复用旧激活或旧分配编号。 */
class ActivationQuotaReplacementTest {

    private final ActivationRepository activations = Mockito.mock(ActivationRepository.class);
    private final ActivationQuotaAllocationRepository allocations =
            Mockito.mock(ActivationQuotaAllocationRepository.class);
    private final GrantRepository grants = Mockito.mock(GrantRepository.class);
    private ActivationEntity oldActivation;
    private ActivationQuotaAllocationEntity oldAllocation;
    private LicenseSubjectController controller;

    @BeforeEach
    void setUp() {
        GrantRevisionEntity grant = new GrantRevisionEntity("gr-replace", "customer-example",
                "product-example", "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null);
        oldActivation = new ActivationEntity("act-old", grant.getGrantId(), "deployment-1", "{}");
        oldAllocation = new ActivationQuotaAllocationEntity("alloc-old", grant.getGrantId(),
                oldActivation.getActivationId(), grant.getGrantRevision(), "localSeats", 2L,
                "FIXED", 9000L, 1234L);
        when(activations.findById(oldActivation.getActivationId())).thenReturn(Optional.of(oldActivation));
        when(activations.save(any(ActivationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(grants.findById(grant.getGrantId())).thenReturn(Optional.of(grant));
        when(allocations.findByActivationIdOrderByMetricAsc(anyString()))
                .thenAnswer(invocation -> oldActivation.getActivationId().equals(invocation.getArgument(0))
                        ? Collections.singletonList(oldAllocation)
                        : Collections.<ActivationQuotaAllocationEntity>emptyList());
        when(allocations.saveAll(any())).thenAnswer(invocation -> {
            List<ActivationQuotaAllocationEntity> values = new ArrayList<ActivationQuotaAllocationEntity>();
            for (ActivationQuotaAllocationEntity value : (Iterable<ActivationQuotaAllocationEntity>) invocation
                    .getArgument(0)) {
                values.add(value);
            }
            return values;
        });
        controller = new LicenseSubjectController(Mockito.mock(SubjectAssignmentRepository.class),
                activations, grants, allocations, null);
    }

    @Test
    void replacementCopiesQuotaFactsAndStartsNewActivationGeneration() {
        String result = controller.replaceActivation(oldActivation.getActivationId(),
                "{\"expectedRowVersion\":0}", TestPlans.all("manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated()).getBody();

        assertThat(oldActivation.getState()).isEqualTo("REPLACED");
        assertThat(result).contains("\"activationGeneration\":2");
        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(allocations).saveAll(captor.capture());
        ActivationQuotaAllocationEntity copied = (ActivationQuotaAllocationEntity) captor.getValue().iterator().next();
        assertThat(copied.getAllocationId()).isNotEqualTo(oldAllocation.getAllocationId());
        assertThat(copied.getActivationId()).isNotEqualTo(oldAllocation.getActivationId());
        assertThat(copied.getGrantId()).isEqualTo(oldAllocation.getGrantId());
        assertThat(copied.getGrantRevision()).isEqualTo(oldAllocation.getGrantRevision());
        assertThat(copied.getMetric()).isEqualTo(oldAllocation.getMetric());
        assertThat(copied.getQuantity()).isEqualTo(oldAllocation.getQuantity());
        assertThat(copied.getRetention()).isEqualTo(oldAllocation.getRetention());
        assertThat(copied.getRetainUntil()).isEqualTo(oldAllocation.getRetainUntil());
        assertThat(copied.getWindowAnchorAt()).isEqualTo(oldAllocation.getWindowAnchorAt());
    }
}
