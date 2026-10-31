package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseSubjectController;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Arrays;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 真实 MySQL 固定资格治理：撤旧建新原子提交、终态不复活、版本不做宽松转换。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(AssignmentReplacementPersistenceTest.ControllerConfiguration.class)
class AssignmentReplacementPersistenceTest {

    @SpyBean
    private SubjectAssignmentRepository assignments;
    @Autowired
    private LicenseSubjectController controller;
    @Autowired
    private GrantRepository grants;
    private SubjectAssignmentEntity original;

    @BeforeEach
    void setUp() {
        String prefix = "ar-" + UUID.randomUUID();
        grants.saveAndFlush(new GrantRevisionEntity(prefix, "customer-example", "product-example",
                "PERSON", "ASSIGNED_SUBJECTS", null, "OFFLINE", "proof-example"));
        original = new SubjectAssignmentEntity(prefix, prefix, 1, "PERSON", "identity-example", "person-example", "[]");
        original.appendMetrics("[]", 7);
        original = assignments.saveAndFlush(original);
    }

    @AfterEach
    void cleanup() {
        assignments.deleteAll(assignments.findByGrantId(original.getGrantId()));
        grants.findById(original.getGrantId()).ifPresent(grants::delete);
        SecurityContextHolder.clearContext();
    }

    @Test
    void replacementKeepsHistoryAndStartsAnIndependentGenerationForTheNewId() {
        String result = controller.replaceAssignment(original.getAssignmentId(), replacementBody("replacement-example"),
                TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()).getBody();
        String replacementId = StrictJson.parse(result, com.fasterxml.jackson.databind.node.ObjectNode.class)
                .path("assignmentId").asText();
        SubjectAssignmentEntity old = assignments.findById(original.getAssignmentId()).get();
        SubjectAssignmentEntity created = assignments.findById(replacementId).get();
        log.info("资格替换真实提交：旧编号保留终态，新编号独立起始代数");
        assertThat(old.getState()).isEqualTo("REPLACED");
        assertThat(old.getAssignmentGeneration()).isEqualTo(7);
        assertThat(created.getAssignmentId()).isNotEqualTo(old.getAssignmentId());
        assertThat(created.getAssignmentGeneration()).isEqualTo(1);
        assertThat(created.getSubjectId()).isEqualTo("replacement-example");
        assertThat(created.getState()).isEqualTo("ACTIVE");
    }

    @Test
    void failedReplacementInsertRollsBackTheOldState() {
        // 原资格真实落库；只在新记录写入点注入故障，保证覆盖撤旧之后的事务回滚。
        IllegalStateException insertFailure = new IllegalStateException("example-insert-failure");
        doThrow(insertFailure).when(assignments).save(argThat(entity ->
                entity != null && !original.getAssignmentId().equals(entity.getAssignmentId())));
        assertThatThrownBy(() -> controller.replaceAssignment(original.getAssignmentId(), replacementBody("replacement-example"),
                TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isSameAs(insertFailure);
        log.info("替换新记录写入故障后真实回读原资格仍ACTIVE，不遗留只撤旧未建新的状态");
        assertThat(assignments.findById(original.getAssignmentId()).get().getState()).isEqualTo("ACTIVE");
        assertThat(assignments.findByGrantId(original.getGrantId())).hasSize(1);
    }

    @Test
    void terminalAssignmentsCannotBeRestoredReplacedOrExpanded() {
        for (String state : Arrays.asList("REPLACED", "REVOKED")) {
            original.setState(state);
            original = assignments.saveAndFlush(original);
            String version = "\"expectedRowVersion\":" + original.getRowVersion();
            assertStatus(() -> controller.patchAssignmentState(original.getAssignmentId(),
                    "{" + version + ",\"state\":\"ACTIVE\"}", TestPlans.all("manage"),
                    LicenseIdentityHttpTest.TestRequests.authenticated()), 409);
            assertStatus(() -> controller.replaceAssignment(original.getAssignmentId(), replacementBody("replacement-example"),
                    TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()), 409);
            assertStatus(() -> controller.appendMetrics(original.getAssignmentId(),
                    "{" + version + ",\"requestedNamedMetrics\":[\"metricExample\"]}", "example-idempotency",
                    TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()), 409);
            assertThat(assignments.findById(original.getAssignmentId()).get().getState()).isEqualTo(state);
            assertThat(assignments.findByGrantId(original.getGrantId())).hasSize(1);
        }
    }

    @Test
    void malformedVersionsAndMetricShapeDoNotMutateTheQualification() {
        for (String version : Arrays.asList("null", "\"0\"", "0.5", "-1", "9223372036854775808")) {
            assertStatus(() -> controller.patchAssignmentState(original.getAssignmentId(),
                    "{\"expectedRowVersion\":" + version + ",\"state\":\"SUSPENDED\"}", TestPlans.all("manage"),
                    LicenseIdentityHttpTest.TestRequests.authenticated()), 422);
        }
        assertStatus(() -> controller.appendMetrics(original.getAssignmentId(),
                "{\"expectedRowVersion\":" + original.getRowVersion() + ",\"requestedNamedMetrics\":{}}",
                "example-idempotency", TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()), 422);
        assertThat(assignments.findById(original.getAssignmentId()).get().getState()).isEqualTo("ACTIVE");
        assertThat(assignments.findById(original.getAssignmentId()).get().getAssignmentGeneration()).isEqualTo(7);
    }

    @Test
    void explicitGrantRevisionMustMatchTheCurrentGrantForCreationAndExpansion() {
        grants.saveAndFlush(new GrantRevisionEntity(original.getGrantId(), "customer-example", "product-example",
                "PERSON", "ASSIGNED_SUBJECTS", null, "OFFLINE", "proof-example"));
        String creation = "{\"grantRevision\":2,\"subjectKind\":\"PERSON\",\"sourceId\":\"identity-example\","
                + "\"subjectId\":\"another-example\",\"requestedNamedMetrics\":[]}";
        assertStatus(() -> controller.createAssignment(original.getGrantId(), creation, "example-idempotency",
                TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()), 409);
        assertStatus(() -> controller.createAssignment(original.getGrantId(), creation.replace("\":2", "\":\"1\""),
                "example-idempotency", TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()), 422);
        assertStatus(() -> controller.appendMetrics(original.getAssignmentId(),
                "{\"expectedRowVersion\":" + original.getRowVersion()
                        + ",\"grantRevision\":2,\"requestedNamedMetrics\":[\"metricExample\"]}",
                "example-idempotency", TestPlans.all("manage"), LicenseIdentityHttpTest.TestRequests.authenticated()), 409);
        assertThat(assignments.findByGrantId(original.getGrantId())).hasSize(1);
        assertThat(assignments.findById(original.getAssignmentId()).get().getAssignmentGeneration()).isEqualTo(7);
    }

    private String replacementBody(String subjectId) {
        return "{\"sourceId\":\"identity-example\",\"subjectId\":\"" + subjectId
                + "\",\"expectedRowVersion\":" + original.getRowVersion() + "}";
    }

    private static void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(status);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControllerConfiguration {
        @Bean
        LicenseSubjectController assignmentGovernanceController(SubjectAssignmentRepository assignments,
                                                               ActivationRepository activations,
                                                               GrantRepository grants) {
            return new LicenseSubjectController(assignments, activations, grants);
        }
    }
}
