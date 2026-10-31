package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseSubjectController;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 激活治理合同：管理端显式登记调用者、DATA与版本围栏、存量补绑和换机继承。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseActivationCallerTest {

    private static final String BINDING = "\"callerBinding\":{\"sourceId\":\"aksk\",\"subjectId\":\"product-machine\"}";
    private final ActivationRepository activations = mock(ActivationRepository.class);
    private final GrantRepository grants = mock(GrantRepository.class);
    private final SubjectAssignmentRepository assignments = mock(SubjectAssignmentRepository.class);
    private LicenseSubjectController controller;
    private ActivationEntity existing;

    @BeforeEach
    void setUp() {
        controller = new LicenseSubjectController(assignments, activations, grants);
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant("ONLINE")));
        existing = new ActivationEntity("act-1", "gr-1", "deployment-1", "{}");
        when(activations.findById("act-1")).thenReturn(Optional.of(existing));
        when(activations.save(any())).thenAnswer(call -> call.getArgument(0));
        when(activations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlineActivationRequiresExplicitManagedCallerAndReturnsNoneDeviceCapability() {
        log.info("在线激活必须显式登记许可服务，响应不假称支持设备绑定");
        assertStatus(() -> controller.createActivation("gr-1", "{\"deploymentCode\":\"deployment-1\"}",
                "idem-1", TestPlans.all("manage"), auth()), 422);
        String result = controller.createActivation("gr-1", "{\"deploymentCode\":\"deployment-1\"," + BINDING + "}",
                "idem-1", TestPlans.all("manage"), auth()).getBody();
        assertThat(result).contains("\"sourceId\":\"aksk\"", "\"subjectId\":\"product-machine\"",
                "\"deviceBindingMode\":\"NONE\"");
    }

    @Test
    void offlineActivationMayRemainUnboundAndSelfReportedDeviceDigestsAreRejected() {
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant("OFFLINE")));
        String result = controller.createActivation("gr-1", "{\"deploymentCode\":\"deployment-1\"}",
                "idem-1", TestPlans.all("manage"), auth()).getBody();
        log.info("纯离线可以不配置在线调用者，但当前NONE政策拒绝自报设备摘要");
        assertThat(result).contains("\"callerBinding\":null");
        assertStatus(() -> controller.createActivation("gr-1",
                "{\"deploymentCode\":\"deployment-1\",\"componentDigests\":{\"cpu\":\"fake-digest\"}}",
                "idem-2", TestPlans.all("manage"), auth()), 422);
    }

    @Test
    void explicitNullCallerIsAllowedOnlyForOfflineCreation() {
        String body = "{\"deploymentCode\":\"deployment-example\",\"callerBinding\":null}";
        assertStatus(() -> controller.createActivation("gr-1", body, "idem-example", TestPlans.all("manage"), auth()), 422);
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant("OFFLINE")));
        assertThat(controller.createActivation("gr-1", body, "idem-example", TestPlans.all("manage"), auth()).getBody())
                .contains("\"callerBinding\":null");
    }

    @Test
    void terminalActivationsCannotBeReactivatedThroughStatePatch() {
        for (String state : Arrays.asList("REPLACED", "REVOKED")) {
            existing.setState(state);
            assertStatus(() -> controller.patchActivationState("act-1", "{\"expectedRowVersion\":0,\"state\":\"ACTIVE\"}",
                    TestPlans.all("manage"), auth()), 409);
            assertThat(existing.getState()).isEqualTo(state);
        }
        verify(activations, never()).save(any());
    }

    @Test
    void legacyBindingCanBeManagedExplicitlyAndDataDenialNeverMutatesIt() {
        log.info("存量补绑受grant DATA管理范围保护，越界不改绑");
        assertStatus(() -> controller.patchActivationCaller("act-1", "{\"expectedRowVersion\":0," + BINDING + "}",
                TestPlans.restricted("manage", "gr-other"), auth()), 404);
        assertStatus(() -> controller.patchActivationCaller("act-missing", "{\"expectedRowVersion\":0," + BINDING + "}",
                TestPlans.restricted("manage", "gr-other"), auth()), 404);
        assertThat(existing.getCallerSourceId()).isNull();
        verify(activations, never()).saveAndFlush(any());
        String result = controller.patchActivationCaller("act-1", "{\"expectedRowVersion\":0," + BINDING + "}",
                TestPlans.all("manage"), auth()).getBody();
        assertThat(result).contains("product-machine");
        assertThat(existing.getCallerSourceId()).isEqualTo("aksk");
        assertThat(existing.getCallerSubjectId()).isEqualTo("product-machine");
    }

    @Test
    void assignmentOutsideGrantDataScopeIsHiddenAsNotFound() {
        when(assignments.findById("assignment-foreign")).thenReturn(Optional.of(new SubjectAssignmentEntity(
                "assignment-foreign", "gr-other", 1L, "PERSON", "iam", "person-1", "[]")));
        assertStatus(() -> controller.getAssignment("assignment-foreign",
                TestPlans.restricted("read", "gr-visible"), auth()), 404);
        assertStatus(() -> controller.getAssignment("assignment-missing",
                TestPlans.restricted("read", "gr-visible"), auth()), 404);
    }

    @Test
    void malformedOrStaleVersionAndMalformedCallerNeverChangeBinding() {
        log.info("管理绑定严格校验版本及嵌套身份，非法输入不写台账");
        for (String version : Arrays.asList("null", "\"0\"", "0.5", "-1", "9223372036854775808")) {
            assertStatus(() -> controller.patchActivationCaller("act-1",
                    "{\"expectedRowVersion\":" + version + "," + BINDING + "}", TestPlans.all("manage"), auth()), 422);
        }
        assertStatus(() -> controller.patchActivationCaller("act-1", "{\"expectedRowVersion\":1," + BINDING + "}",
                TestPlans.all("manage"), auth()), 409);
        for (String binding : Arrays.asList("null", "[]", "{\"sourceId\":\"aksk\"}",
                "{\"sourceId\":\"aksk\",\"subjectId\":\"product-machine\",\"kind\":\"PERSON\"}")) {
            assertStatus(() -> controller.patchActivationCaller("act-1",
                    "{\"expectedRowVersion\":0,\"callerBinding\":" + binding + "}", TestPlans.all("manage"), auth()), 422);
        }
        assertThat(existing.getCallerSubjectId()).isNull();
        verify(activations, never()).saveAndFlush(any());
    }

    @Test
    void replacementKeepsApprovedCallerBumpsGenerationAndPreservesOldHistory() {
        existing.bindCaller("aksk", "product-machine");
        String result = controller.replaceActivation("act-1", "{\"expectedRowVersion\":0}",
                TestPlans.all("manage"), auth()).getBody();
        log.info("换机沿用受控caller并增代数，旧历史转REPLACED不复用");
        assertThat(existing.getState()).isEqualTo("REPLACED");
        assertThat(existing.getActivationGeneration()).isEqualTo(1L);
        assertThat(result).contains("\"activationGeneration\":2", "product-machine", "\"state\":\"ACTIVE\"");
        assertStatus(() -> controller.replaceActivation("act-1", "{\"expectedRowVersion\":0}",
                TestPlans.all("manage"), auth()), 409);
        assertStatus(() -> controller.patchActivationCaller("act-1", "{\"expectedRowVersion\":0," + BINDING + "}",
                TestPlans.all("manage"), auth()), 409);
    }

    private static GrantRevisionEntity grant(String mode) {
        return new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS",
                null, mode, null);
    }

    private javax.servlet.http.HttpServletRequest auth() {
        return LicenseIdentityHttpTest.TestRequests.authenticated();
    }

    private static void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(status);
    }
}
