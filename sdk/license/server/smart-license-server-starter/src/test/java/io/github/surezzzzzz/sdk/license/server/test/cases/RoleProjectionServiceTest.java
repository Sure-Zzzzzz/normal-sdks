package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.spi.RoleProjectionPort;
import io.github.surezzzzzz.sdk.license.server.entity.*;
import io.github.surezzzzzz.sdk.license.server.integration.RoleProjectionService;
import io.github.surezzzzzz.sdk.license.server.repository.*;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.*;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 商业贡献聚合、批准快照、真实挂载和持久执行围栏的业务回归；不以mock声明HTTP联调完成。 */
@Slf4j
class RoleProjectionServiceTest {
    private final IntegrationPlanRepository plans = mock(IntegrationPlanRepository.class);
    private final RoleProjectionRepository projections = mock(RoleProjectionRepository.class);
    private final GrantRepository grants = mock(GrantRepository.class);
    private final SubjectAssignmentRepository assignments = mock(SubjectAssignmentRepository.class);
    private final LicenseRevocationGuard revocations = mock(LicenseRevocationGuard.class);
    private final SubjectAdmissionService admission = mock(SubjectAdmissionService.class);
    private final RoleProjectionPort port = mock(RoleProjectionPort.class);
    private final EntityManager em = mock(EntityManager.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final IntegrationRequestRepository requests = mock(IntegrationRequestRepository.class);
    private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
    private final Map<String, IntegrationPlanEntity> stored = new LinkedHashMap<>();
    private final Map<String, IntegrationRequestEntity> requestStore = new HashMap<>();
    private RoleProjectionService service;
    private RoleProjectionEntity projection;
    private OrganizationBindingEntity binding;
    private ProductEntity product;

    @BeforeEach
    void setup() {
        ObjectProvider<RoleProjectionPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        service = new RoleProjectionService(plans, projections, grants, assignments, revocations, admission, provider, tx, requests, events);
        ReflectionTestUtils.setField(service, "entityManager", em);
        binding = new OrganizationBindingEntity("c1", "environment-1", 10L);
        product = new ProductEntity("p1", "issuer-1", "product-1", "示例产品", "[\"commercial.create\"]", "[]");
        product.setApplicationBindings("[{\"applicationId\":9,\"basePermissionSnapshotVersion\":1}]");
        projection = new RoleProjectionEntity("projection-1", "c1", "p1", 9L, binding);
        when(em.find(CustomerEntity.class, "c1", LockModeType.PESSIMISTIC_WRITE)).thenReturn(new CustomerEntity("c1", "c1", "示例客户"));
        when(em.find(ProductEntity.class, "p1", LockModeType.PESSIMISTIC_READ)).thenReturn(product);
        when(em.find(ProductEntity.class, "p1")).thenReturn(product);
        when(em.find(OrganizationBindingEntity.class, "c1", LockModeType.PESSIMISTIC_READ)).thenReturn(binding);
        when(projections.lockTarget("c1", "p1", 9L)).thenAnswer(call -> Optional.of(projection));
        when(projections.save(any())).thenAnswer(call -> call.getArgument(0));
        when(projections.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(plans.save(any())).thenAnswer(call -> { IntegrationPlanEntity value = call.getArgument(0); stored.put(value.getPlanId(), value); return value; });
        when(plans.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(plans.findById(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(plans.findByCommandId(anyString())).thenAnswer(call -> stored.values().stream()
                .filter(plan -> plan.getCommandId().equals(call.getArgument(0))).findFirst());
        when(plans.findByCustomerIdAndProductIdAndApplicationIdOrderByDesiredRevisionDesc("c1", "p1", "9"))
                .thenAnswer(call -> new ArrayList<>(stored.values()));
        when(port.manifest(9L)).thenReturn(new RoleProjectionPort.Manifest(9L, "example-app", 1L, "digest-1",
                Collections.singletonList("app.page"), Collections.singletonList("app.read")));
        when(port.departments(10L)).thenReturn(Arrays.asList(new RoleProjectionPort.Department(10L, null, true),
                new RoleProjectionPort.Department(11L, 10L, true)));
        when(grants.findByCustomerIdAndProductIdOrderByGrantId("c1", "p1")).thenReturn(Collections.singletonList(grant("g1", "PERSON")));
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(requests.findById(anyString())).thenAnswer(call -> Optional.ofNullable(requestStore.get(call.getArgument(0))));
        when(requests.saveAndFlush(any())).thenAnswer(call -> {
            IntegrationRequestEntity request = call.getArgument(0); requestStore.put(request.getRequestDigest(), request); return request;
        });
    }

    @Test
    void approvedPermissionSnapshotAndRealDepartmentsProducePlanWithoutWritingIam() {
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        log.debug("核验真实目录清单与批准PAGE/API，生成阶段无IAM写入");
        assertThat(plan.getDesiredRules()).contains("app.read").doesNotContain("commercial.create");
        assertThat(plan.getDepartmentDiff()).contains("\"toMount\":[10,11]");
        assertThat(snapshot(plan).path("organizationInstanceId").asText()).isEqualTo("environment-1");
        verify(port, never()).create(anyString(), anyLong(), anyLong(), anyString());
        verify(port, never()).putRules(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void removingOneOfTwoPersonGrantsPreservesTheOtherContribution() {
        when(grants.findByCustomerIdAndProductIdOrderByGrantId("c1", "p1"))
                .thenReturn(Arrays.asList(grant("g1", "PERSON"), grant("g2", "PERSON")));
        when(revocations.isRevoked(io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind.GRANT, "g1")).thenReturn(true);
        ObjectNode snapshot = snapshot(service.create("c1", "p1", 9L, rules()));
        assertThat(snapshot.path("contributionPresent").asBoolean()).isTrue();
        assertThat(snapshot.path("contributions").toString()).contains("g2").doesNotContain("g1");
        assertThat(snapshot.path("targetDepartmentIds").size()).isEqualTo(2);
    }

    @Test
    void deploymentAndServiceNeverGiveCustomerPersonnelAnEntryRole() {
        when(grants.findByCustomerIdAndProductIdOrderByGrantId("c1", "p1"))
                .thenReturn(Arrays.asList(grant("g1", "DEPLOYMENT"), grant("g2", "SERVICE")));
        ObjectNode snapshot = snapshot(service.create("c1", "p1", 9L, rules()));
        assertThat(snapshot.path("contributionPresent").asBoolean()).isFalse();
        assertThat(snapshot.path("desiredRules").path("apiPermissions").size()).isZero();
        assertThat(snapshot.path("targetDepartmentIds").size()).isZero();
    }

    @Test
    void manifestSubsetAndEmptyDataObjectAreRejectedBeforeCreatingPlan() {
        ObjectNode unknown = rules(); unknown.putArray("apiPermissions").add("iam.admin");
        assertThatThrownBy(() -> service.create("c1", "p1", 9L, unknown)).isInstanceOf(LicenseHttpException.class);
        ObjectNode emptyData = rules(); emptyData.putObject("dataGrantTemplate");
        assertThatThrownBy(() -> service.create("c1", "p1", 9L, emptyData)).isInstanceOf(LicenseHttpException.class);
        assertThat(stored).isEmpty();
    }

    @Test
    void changedEnvironmentCannotReuseOldRoleOrPlan() {
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        binding.rebind("environment-2", 10L);
        assertThatThrownBy(() -> service.confirm(plan.getPlanId(), 1L, "environment-1", 1, "role-1"))
                .isInstanceOf(LicenseHttpException.class);
        assertThat(projection.getState()).isEqualTo("ENVIRONMENT_RECONFIRM_REQUIRED");
        verify(port, never()).read(anyString());
    }

    @Test
    void manualReceiptRequiresExactActualRoleAndStaysManual() {
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        when(port.read("role-1")).thenReturn(role(3L, rules().toString(), Arrays.asList(10L, 11L)));
        IntegrationPlanEntity confirmed = service.confirm(plan.getPlanId(), 1L, "environment-1", 1, "role-1");
        assertThat(confirmed.getState()).isEqualTo("MANUALLY_CONFIRMED");
        assertThat(projection.getOpenRoleId()).isEqualTo("role-1");
        assertThat(confirmed.getConfirmedRevision()).isEqualTo(1L);
    }

    @Test
    void actualManifestRevisionChangeRemainsStaleWithoutLosingLargeIntegerPrecision() {
        long version = 9007199254740992L;
        when(port.manifest(9L)).thenReturn(new RoleProjectionPort.Manifest(9L, "example-app", version, "digest-1",
                Collections.singletonList("app.page"), Collections.singletonList("app.read")));
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        when(port.manifest(9L)).thenReturn(new RoleProjectionPort.Manifest(9L, "example-app", version + 1, "digest-1",
                Collections.singletonList("app.page"), Collections.singletonList("app.read")));

        assertThatThrownBy(() -> service.attempt(plan.getCommandId(), "AUTO"))
                .isInstanceOf(LicenseHttpException.class);
        assertThat(projection.getState()).isEqualTo("PENDING_EXECUTION");
        verify(port, never()).create(anyString(), anyLong(), anyLong(), anyString());
        verify(port, never()).putRules(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void changedGrantRevisionInvalidatesManualReceiptAndAutomaticExecution() {
        GrantRevisionEntity contribution = grant("g1", "PERSON");
        when(grants.findByCustomerIdAndProductIdOrderByGrantId("c1", "p1"))
                .thenReturn(Collections.singletonList(contribution));
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        contribution.setGrantRevision(2L);

        assertThatThrownBy(() -> service.confirm(plan.getPlanId(), 1L, "environment-1", 1, "role-1"))
                .isInstanceOf(LicenseHttpException.class);
        assertThatThrownBy(() -> service.attempt(plan.getCommandId(), "AUTO"))
                .isInstanceOf(LicenseHttpException.class);
        verify(port, never()).read("role-1");
        verify(port, never()).create(anyString(), anyLong(), anyLong(), anyString());
        verify(port, never()).putRules(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void durableUnknownFencePrecedesTheFirstRemoteWriteAndBlocksRetry() {
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        when(port.create("projection-1", 9L, 10L, "lic_projection-1"))
                .thenThrow(new RoleProjectionPort.Failure(RoleProjectionPort.FailureKind.UNCERTAIN, null));
        IntegrationPlanEntity result = service.attempt(plan.getCommandId(), "AUTO");
        assertThat(result.getState()).isEqualTo("UNCERTAIN");
        InOrder ordering = inOrder(plans, port);
        ordering.verify(plans).saveAndFlush(plan);
        ordering.verify(port).create("projection-1", 9L, 10L, "lic_projection-1");
        assertThatThrownBy(() -> service.attempt(plan.getCommandId(), "AUTO")).isInstanceOf(LicenseHttpException.class);
        verify(port, times(1)).create(anyString(), anyLong(), anyLong(), anyString());
    }

    @Test
    void revisionConflictRefreshesAPlanWithoutBlindlyWritingAgain() {
        projection.bind("role-1", "lic-role-1"); projection.acknowledge(null, "[]", 1L);
        when(port.read("role-1")).thenReturn(role(1L, null, Collections.emptyList()));
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        doThrow(new RoleProjectionPort.Failure(RoleProjectionPort.FailureKind.REVISION_CONFLICT, null))
                .when(port).putRules(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString());
        IntegrationPlanEntity refreshed = service.attempt(plan.getCommandId(), "AUTO");
        assertThat(refreshed.getDesiredRevision()).isEqualTo(2L);
        assertThat(refreshed.getState()).isEqualTo("PENDING_EXECUTION");
        verify(port, times(1)).putRules(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString());
        verify(port, never()).mount(anyString(), anyLong(), anyLong());
    }

    @Test
    void repeatedCreateDoesNotSupersedeItsOwnPlanAndDifferentInputConflicts() {
        IntegrationPlanEntity created = service.create("c1", "p1", 9L, rules(), "request-1", "input-1");
        IntegrationPlanEntity replay = service.create("c1", "p1", 9L, rules(), "request-1", "input-1");
        assertThat(replay.getPlanId()).isEqualTo(created.getPlanId());
        assertThat(projection.getDesiredRevision()).isEqualTo(1L);
        assertThatThrownBy(() -> service.create("c1", "p1", 9L, rules(), "request-1", "input-2"))
                .isInstanceOf(LicenseHttpException.class);
        assertThat(stored).hasSize(1);
    }

    @Test
    void repeatingAnUnknownAutoRequestReturnsItsRecordedResultWithoutAnotherCall() {
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        when(port.create(anyString(), anyLong(), anyLong(), anyString()))
                .thenThrow(new RoleProjectionPort.Failure(RoleProjectionPort.FailureKind.UNCERTAIN, null));
        IntegrationPlanEntity first = service.attempt(plan.getCommandId(), "AUTO", "attempt-1", "auto-input");
        IntegrationPlanEntity replay = service.attempt(plan.getCommandId(), "AUTO", "attempt-1", "auto-input");
        assertThat(first.getState()).isEqualTo("UNCERTAIN");
        assertThat(replay.getPlanId()).isEqualTo(first.getPlanId());
        verify(port, times(1)).create(anyString(), anyLong(), anyLong(), anyString());
    }

    @Test
    void uncertainWritesRequireTerminationDeclarationAndExactManualReadback() {
        IntegrationPlanEntity plan = service.create("c1", "p1", 9L, rules());
        projection.mark("UNCERTAIN");
        plan.execution("AUTO", IntegrationState.UNCERTAIN, "TEST_UNKNOWN");
        assertThatThrownBy(() -> service.reconcile(plan.getCommandId(), 1L, "environment-1", 1, "role-1", false))
                .isInstanceOf(LicenseHttpException.class);
        when(port.read("role-1")).thenReturn(role(3L, rules().toString(), Arrays.asList(10L, 11L)));
        IntegrationPlanEntity receipt = service.reconcile(plan.getCommandId(), 1L, "environment-1", 1, "role-1", true);
        assertThat(receipt.getState()).isEqualTo("MANUALLY_CONFIRMED");
        assertThat(receipt.getDesiredRevision()).isEqualTo(2L);
        assertThat(projection.getRemoteRevision()).isEqualTo(3L);
        verify(port, never()).putRules(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void reconfirmedEnvironmentAllocatesANewExternalIdentityWithoutTouchingTheOldRole() {
        projection.bind("old-role", "old-code"); projection.acknowledge(null, "[]", 1L);
        String previousExternalId = projection.getExternalId();
        binding.rebind("environment-2", 10L);
        service.reconfirmEnvironment("c1", "p1", 9L, "environment-2", 2, rules(), true);
        assertThat(projection.getExternalId()).isNotEqualTo(previousExternalId);
        assertThat(projection.getOpenRoleId()).isNull();
        assertThat(projection.getBindingGeneration()).isEqualTo(2);
        verify(port, never()).read("old-role");
        verify(port, never()).create(anyString(), anyLong(), anyLong(), anyString());
    }

    private static GrantRevisionEntity grant(String id, String kind) {
        GrantRevisionEntity grant = new GrantRevisionEntity(id, "c1", "p1", kind, "CUSTOMER_MEMBERS", null, "ONLINE", null);
        grant.setState("ENABLED"); return grant;
    }
    private static ObjectNode rules() { return StrictJson.parse("{\"pagePermissions\":[\"app.page\"],\"apiPermissions\":[\"app.read\"],\"dataGrantTemplate\":null}", ObjectNode.class); }
    private static ObjectNode snapshot(IntegrationPlanEntity plan) { return StrictJson.parse(plan.getProjectionSnapshot(), ObjectNode.class); }
    private static RoleProjectionPort.Role role(long revision, String rules, List<Long> departments) {
        return new RoleProjectionPort.Role("role-1", "projection-1", "lic-role-1", 9L, 10L, revision, rules, departments);
    }
}
