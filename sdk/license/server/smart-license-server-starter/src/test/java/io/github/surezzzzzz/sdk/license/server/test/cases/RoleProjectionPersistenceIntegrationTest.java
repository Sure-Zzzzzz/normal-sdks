package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.spi.RoleProjectionPort;
import io.github.surezzzzzz.sdk.license.server.entity.*;
import io.github.surezzzzzz.sdk.license.server.integration.RoleProjectionService;
import io.github.surezzzzzz.sdk.license.server.repository.*;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实MySQL验证独立事务占位与并发幂等；身份调用为受控替身，不宣称真实IAM联调。 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RoleProjectionPersistenceIntegrationTest {
    @Autowired private IntegrationPlanRepository plans;
    @Autowired private RoleProjectionRepository projections;
    @Autowired private GrantRepository grants;
    @Autowired private SubjectAssignmentRepository assignments;
    @Autowired private IntegrationRequestRepository requests;
    @Autowired private CustomerRepository customers;
    @Autowired private ProductRepository products;
    @Autowired private OrganizationBindingRepository bindings;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void executionCrashLeavesCommittedUnknownStateAndPreventsASecondRemoteWrite() {
        Fixture fixture = fixture();
        try {
            RoleProjectionService service = service(fixture.port);
            IntegrationPlanEntity plan = new TransactionTemplate(transactions).execute(status ->
                    service.create(fixture.customerId, fixture.productId, 9L, rules()));
            doThrow(new AssertionError("模拟执行事务终止")).when(fixture.port).create(anyString(), anyLong(), anyLong(), anyString());
            assertThatThrownBy(() -> service.attempt(plan.getCommandId(), "AUTO")).isInstanceOf(AssertionError.class);
            IntegrationPlanEntity persisted = plans.findById(plan.getPlanId()).get();
            log.info("真实MySQL独立占位：执行事务终止后状态={}", persisted.getState());
            assertThat(persisted.getState()).isEqualTo("UNCERTAIN");
            RoleProjectionService restarted = service(fixture.port);
            assertThatThrownBy(() -> restarted.attempt(plan.getCommandId(), "AUTO"))
                    .isInstanceOf(io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException.class);
            verify(fixture.port, times(1)).create(anyString(), anyLong(), anyLong(), anyString());
        } finally { cleanup(fixture); }
    }

    @Test
    void twoIndependentCreatorsReplayOnePlanUsingTheDatabaseTargetLock() throws Exception {
        Fixture fixture = fixture();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        String requestDigest = "request-" + UUID.randomUUID();
        try {
            RoleProjectionService first = service(fixture.port);
            RoleProjectionService second = service(fixture.port);
            Future<IntegrationPlanEntity> a = workers.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("并发启动未到达"); }
                return new TransactionTemplate(transactions).execute(status -> first.create(
                        fixture.customerId, fixture.productId, 9L, rules(), requestDigest, "same-input"));
            });
            Future<IntegrationPlanEntity> b = workers.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("并发启动未到达"); }
                return new TransactionTemplate(transactions).execute(status -> second.create(
                        fixture.customerId, fixture.productId, 9L, rules(), requestDigest, "same-input"));
            });
            start.countDown();
            IntegrationPlanEntity left = a.get(20, TimeUnit.SECONDS);
            IntegrationPlanEntity right = b.get(20, TimeUnit.SECONDS);
            log.info("真实MySQL并发角色计划：同计划={}，修订={}/{}", left.getPlanId().equals(right.getPlanId()),
                    left.getDesiredRevision(), right.getDesiredRevision());
            assertThat(left.getPlanId()).isEqualTo(right.getPlanId());
            assertThat(left.getDesiredRevision()).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM license_role_projection WHERE customer_id=?", Long.class,
                    fixture.customerId)).isEqualTo(1L);
            assertThat(plans.findByCustomerId(fixture.customerId)).hasSize(1);
        } finally {
            workers.shutdownNow();
            try { assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
            finally { cleanup(fixture); }
        }
    }

    private RoleProjectionService service(RoleProjectionPort port) {
        ObjectProvider<RoleProjectionPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        RoleProjectionService service = new RoleProjectionService(plans, projections, grants, assignments,
                mock(LicenseRevocationGuard.class), mock(SubjectAdmissionService.class), provider, transactions,
                requests, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
    }

    private Fixture fixture() {
        String id = UUID.randomUUID().toString();
        Fixture fixture = new Fixture("cu-rp-" + id, "prod-rp-" + id);
        customers.saveAndFlush(new CustomerEntity(fixture.customerId, "rp-" + id, "示例客户"));
        ProductEntity product = new ProductEntity(fixture.productId, "issuer-rp", "rp-" + id, "示例产品", "[]", "[]");
        product.setApplicationBindings("[{\"applicationId\":9,\"basePermissionSnapshotVersion\":1}]");
        products.saveAndFlush(product);
        bindings.saveAndFlush(new OrganizationBindingEntity(fixture.customerId, "test-environment", 10L));
        GrantRevisionEntity grant = new GrantRevisionEntity("grant-rp-" + id, fixture.customerId, fixture.productId,
                "PERSON", "CUSTOMER_MEMBERS", null, "ONLINE", null);
        grant.setState("ENABLED"); grants.saveAndFlush(grant);
        when(fixture.port.manifest(9L)).thenReturn(new RoleProjectionPort.Manifest(9L, "test-app", 1L, "digest-1",
                Collections.singletonList("app.page"), Collections.singletonList("app.read")));
        when(fixture.port.departments(10L)).thenReturn(Arrays.asList(new RoleProjectionPort.Department(10L, null, true),
                new RoleProjectionPort.Department(11L, 10L, true)));
        return fixture;
    }

    private void cleanup(Fixture fixture) {
        jdbc.update("DELETE FROM license_integration_request WHERE plan_id IN (SELECT plan_id FROM license_integration_plan WHERE customer_id=?)", fixture.customerId);
        jdbc.update("DELETE FROM license_integration_plan WHERE customer_id=?", fixture.customerId);
        jdbc.update("DELETE FROM license_role_projection WHERE customer_id=?", fixture.customerId);
        jdbc.update("DELETE FROM license_grant WHERE customer_id=?", fixture.customerId);
        bindings.deleteById(fixture.customerId); products.deleteById(fixture.productId); customers.deleteById(fixture.customerId);
    }

    private static ObjectNode rules() {
        return StrictJson.parse("{\"pagePermissions\":[\"app.page\"],\"apiPermissions\":[\"app.read\"],\"dataGrantTemplate\":null}", ObjectNode.class);
    }
    private static final class Fixture {
        final String customerId;
        final String productId;
        final RoleProjectionPort port = mock(RoleProjectionPort.class);
        Fixture(String customerId, String productId) { this.customerId = customerId; this.productId = productId; }
    }
}
