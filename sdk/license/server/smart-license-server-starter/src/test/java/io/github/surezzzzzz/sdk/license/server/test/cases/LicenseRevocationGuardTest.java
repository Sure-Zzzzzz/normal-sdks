package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.spi.LicenseSigner;
import io.github.surezzzzzz.sdk.license.server.constant.OperationState;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseGrantController;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.service.CredentialIssuanceService;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.OfflineCredentialComposer;
import io.github.surezzzzzz.sdk.license.server.service.OperationRepository;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 五类吊销效力回归：真实领域归属、在线对象准入、冻结原文保持、签名期间撤回不得交付。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseRevocationGuardTest {

    private final RevocationRepository revocations = mock(RevocationRepository.class);
    private final GrantRepository grants = mock(GrantRepository.class);
    private final SigningOperationRepository operations = mock(SigningOperationRepository.class);
    private final ActivationRepository activations = mock(ActivationRepository.class);
    private final SubjectAssignmentRepository assignments = mock(SubjectAssignmentRepository.class);
    private final SigningProfileRepository profiles = mock(SigningProfileRepository.class);
    private LicenseRevocationGuard guard;
    private GrantRevisionEntity grant;
    private SigningProfileEntity profile;
    private SigningOperationEntity operation;

    @BeforeEach
    void setUp() {
        guard = new LicenseRevocationGuard(revocations, grants, operations, activations, assignments, profiles);
        grant = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS",
                null, "OFFLINE", null);
        grant.setState("ENABLED");
        profile = new SigningProfileEntity("sp-1", "prod-1", "CREDENTIAL", "kid-test", "key-test", 1, "00");
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant));
        when(grants.existsById("gr-1")).thenReturn(true);
        when(profiles.findById("sp-1")).thenReturn(Optional.of(profile));
        when(activations.findById("act-1")).thenReturn(Optional.of(
                new ActivationEntity("act-1", "gr-1", "deployment-test", "{}")));
        String payload = OfflineCredentialComposer.compose(grant,
                new ProductEntity("prod-1", "iss-1", "product-test", "示例产品", "[\"read\"]", "[]"),
                LicenseTestModels.publishedSnapshot(),
                "{\"kind\":\"DEPLOYMENT\",\"activationId\":\"act-1\",\"activationGeneration\":1}",
                "cred-1", java.time.Instant.now().getEpochSecond());
        operation = new SigningOperationEntity("op-1", "cred-1", "gr-1", "sp-1",
                Base64Url.encode("{\"alg\":\"ES256\",\"kid\":\"kid-test\",\"typ\":\"license+jwt\"}"
                        .getBytes(StandardCharsets.UTF_8)),
                Base64Url.encode(payload.getBytes(StandardCharsets.UTF_8)), "idem-1");
    }

    @Test
    void validCredentialKeepsFrozenBytesUnchanged() {
        String header = operation.getFrozenHeader();
        String payload = operation.getFrozenPayload();
        guard.requireCredentialUsable(operation);
        log.info("有效凭据通过执行围栏，冻结输入字节不变");
        assertThat(operation.getFrozenHeader()).isEqualTo(header);
        assertThat(operation.getFrozenPayload()).isEqualTo(payload);
    }

    @Test
    void historicalOnlineAndHybridTasksCannotSignOrDeliverOfflineContent() {
        for (String mode : new String[]{"ONLINE", "HYBRID"}) {
            GrantRevisionEntity incompatible = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "DEPLOYMENT",
                    "ALLOCATED_DEPLOYMENTS", null, mode, null);
            incompatible.setState("ENABLED");
            when(grants.findById("gr-1")).thenReturn(Optional.of(incompatible));
            assertThatThrownBy(() -> guard.requireCredentialUsable(operation)).isInstanceOf(LicenseHttpException.class)
                    .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(409);
        }
    }

    @Test
    void grantRevocationBlocksCredential() {
        denyCredential(RevocationTargetKind.GRANT, "gr-1");
    }

    @Test
    void credentialRevocationBlocksDelivery() {
        denyCredential(RevocationTargetKind.CREDENTIAL, "cred-1");
    }

    @Test
    void activationRevocationBlocksCredential() {
        denyCredential(RevocationTargetKind.ACTIVATION, "act-1");
    }

    @Test
    void keyRevocationBlocksFurtherSigningAndDelivery() {
        denyCredential(RevocationTargetKind.SIGNING_KEY, "kid-test");
    }

    @Test
    void suspendedGrantAndChangedRevisionCannotBeDelivered() {
        grant.setState("SUSPENDED");
        log.info("暂停授权不允许交付");
        assertThatThrownBy(() -> guard.requireCredentialUsable(operation)).isInstanceOf(LicenseHttpException.class);
        grant.setState("ENABLED");
        grant.setGrantRevision(2L);
        assertThatThrownBy(() -> guard.requireCredentialUsable(operation)).isInstanceOf(LicenseHttpException.class);
    }

    @Test
    void unknownOrWrongKindTargetCannotBeRevoked() {
        log.info("不存在的凭据与跨种类编号必须404");
        assertThatThrownBy(() -> guard.requireTargetExists(RevocationTargetKind.CREDENTIAL, "gr-1"))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(404);
        assertThatThrownBy(() -> guard.requireTargetExists(RevocationTargetKind.ACTIVATION, "missing"))
                .isInstanceOf(LicenseHttpException.class);
        guard.requireTargetExists(RevocationTargetKind.GRANT, "gr-1");
    }

    @Test
    void onlineActivationRequiresRealGrantOwnershipAndActiveState() {
        OnlineAuthorizationRequest request = deployment();
        ActivationEntity active = new ActivationEntity("act-1", "gr-1", "deployment-test", "{}",
                "aksk", "product-machine");
        when(activations.findById("act-1")).thenReturn(Optional.of(active));
        assertThat(guard.onlineRejection(request, "aksk", "product-machine")).isNull();
        active.setState("SUSPENDED");
        assertThat(guard.onlineRejection(request, "aksk", "product-machine"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        when(activations.findById("act-1")).thenReturn(Optional.of(
                new ActivationEntity("act-1", "gr-foreign", "deployment-test", "{}", "aksk", "product-machine")));
        log.info("在线激活跨授权必须拒绝");
        assertThat(guard.onlineRejection(request, "aksk", "product-machine"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void onlineBindingRequiresCurrentEnabledGrantAndActiveActivation() {
        ActivationEntity active = new ActivationEntity("act-1", "gr-1", "deployment-test", "{}",
                "aksk", "product-machine");
        when(activations.findById("act-1")).thenReturn(Optional.of(active));
        assertThat(guard.onlineBinding(deployment(), "aksk", "product-machine")).isPresent();

        active.setState("SUSPENDED");
        log.info("在线绑定复查激活状态，暂停后不能继续回显绑定");
        assertThat(guard.onlineBinding(deployment(), "aksk", "product-machine")).isEmpty();

        active.setState("ACTIVE");
        grant.setState("SUSPENDED");
        log.info("在线绑定复查授权状态，暂停授权不能继续回显绑定");
        assertThat(guard.onlineBinding(deployment(), "aksk", "product-machine")).isEmpty();
    }

    @Test
    void onlineBindingRejectsRevokedActivationAfterEligibilityCheck() {
        ActivationEntity active = new ActivationEntity("act-1", "gr-1", "deployment-test", "{}",
                "aksk", "product-machine");
        when(activations.findById("act-1")).thenReturn(Optional.of(active));
        revoke(RevocationTargetKind.ACTIVATION, "act-1");

        log.info("在线绑定复查激活吊销，吊销后不能把旧绑定带回响应");
        assertThat(guard.onlineBinding(deployment(), "aksk", "product-machine")).isEmpty();
    }

    @Test
    void deploymentOnlineCannotBorrowOtherCallerAndLegacyUnboundActivationIsDenied() {
        log.info("部署在线使用必须匹配获准来源和服务，存量未绑不能猜调用者");
        assertThat(guard.onlineRejection(deployment(), "aksk", "product-machine"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        when(activations.findById("act-1")).thenReturn(Optional.of(new ActivationEntity(
                "act-1", "gr-1", "deployment-test", "{}", "aksk", "product-machine")));
        assertThat(guard.onlineRejection(deployment(), "aksk", "another-machine"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        assertThat(guard.onlineRejection(deployment(), "another-source", "product-machine"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        assertThat(guard.onlineRejection(deployment(), null, "product-machine"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void storedDeviceDigestIsNotAProofAndCannotMasqueradeAsNonePolicy() {
        log.info("当前NONE设备能力不能用旧摘要、坏摘要或非对象数据冒充证明");
        for (String digests : new String[] {"{\"cpu\":\"self-reported-digest\"}", "[]", "null", "bad-json"}) {
            when(activations.findById("act-1")).thenReturn(Optional.of(new ActivationEntity(
                    "act-1", "gr-1", "deployment-test", digests, "aksk", "product-machine")));
            assertThat(guard.onlineRejection(deployment(), "aksk", "product-machine"))
                    .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        }
    }

    @Test
    void onlineActivationRevocationOverridesActiveState() {
        revoke(RevocationTargetKind.ACTIVATION, "act-1");
        log.info("在线激活吊销优先于ACTIVE");
        assertThat(guard.onlineRejection(deployment(), null, null)).isEqualTo(Decision.ReasonCategory.REVOKED);
    }

    @Test
    void fixedAssignmentCannotBeBorrowedFromAnotherPerson() {
        fixedAssignment();
        OnlineAuthorizationRequest request = person();
        assertThat(guard.onlineRejection(request, "iam", "user-1")).isNull();
        log.info("固定资格本人核对，不能借另一个人的编号");
        assertThat(guard.onlineRejection(request, "iam", "user-other"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        assertThat(guard.onlineRejection(request, "another-source", "user-1"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        assertThat(guard.onlineRejection(request, null, "user-1"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void previousGrantRevisionAssignmentCannotPassOnlineRejection() {
        fixedAssignment();
        grant.setGrantRevision(2L);

        assertThat(guard.onlineRejection(person(), "iam", "user-1"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void onlineBindingRequiresCurrentActiveAssignment() {
        fixedAssignment();
        assertThat(guard.onlineBinding(person(), "iam", "user-1")).isPresent();

        SubjectAssignmentEntity assignment = assignments.findById("sa-1").get();
        assignment.setState("SUSPENDED");
        log.info("在线绑定复查固定资格状态，暂停后不能继续回显绑定");
        assertThat(guard.onlineBinding(person(), "iam", "user-1")).isEmpty();
    }

    @Test
    void onlineBindingRejectsRevokedAssignmentAfterEligibilityCheck() {
        fixedAssignment();
        revoke(RevocationTargetKind.ASSIGNMENT, "sa-1");

        log.info("在线绑定复查固定资格吊销，吊销后不能把旧绑定带回响应");
        assertThat(guard.onlineBinding(person(), "iam", "user-1")).isEmpty();
    }

    @Test
    void fixedAssignmentRevocationBlocksOnlineAndDoesNotAffectOtherGrant() {
        fixedAssignment();
        revoke(RevocationTargetKind.ASSIGNMENT, "sa-1");
        log.info("资格台账吊销即时阻断该资格在线动作");
        assertThat(guard.onlineRejection(person(), null, "user-1")).isEqualTo(Decision.ReasonCategory.REVOKED);
    }

    @Test
    void customerMemberRequiresNonEmptyIamSubject() {
        grant = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "PERSON", "CUSTOMER_MEMBERS", null,
                "ONLINE", null);
        grant.setState("ENABLED");
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant));
        OnlineAuthorizationRequest request = OnlineAuthorizationRequest.personOrService("prod-1", "gr-1",
                SubjectKind.PERSON, "read", null, Collections.emptyMap(), Collections.emptyList());
        assertThat(guard.onlineRejection(request, "iam", null))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        assertThat(guard.onlineRejection(request, "iam", ""))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void customerMemberBindingRequiresCurrentAdmission() {
        grant = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "PERSON", "CUSTOMER_MEMBERS", null,
                "ONLINE", null);
        grant.setState("ENABLED");
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant));
        SubjectAdmissionService admission = mock(SubjectAdmissionService.class);
        when(admission.isAdmitted("cu-1", "user-1")).thenReturn(
                SubjectAdmissionService.Admission.ADMITTED,
                SubjectAdmissionService.Admission.DENIED);
        LicenseRevocationGuard memberGuard = new LicenseRevocationGuard(revocations, grants, operations,
                activations, assignments, profiles, admission);

        log.info("在线最终绑定必须重新核验客户成员资格，成员撤销后不能继续回显绑定");
        assertThat(memberGuard.onlineBinding(personWithoutAssignment(), "iam", "user-1")).isPresent();
        assertThat(memberGuard.onlineBinding(personWithoutAssignment(), "iam", "user-1")).isEmpty();
        org.mockito.Mockito.verify(admission, org.mockito.Mockito.times(2))
                .isAdmitted("cu-1", "user-1");
    }

    @Test
    void customerMemberBindingFailsClosedWithoutAdmissionDependency() {
        grant = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "PERSON", "CUSTOMER_MEMBERS", null,
                "ONLINE", null);
        grant.setState("ENABLED");
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant));

        LicenseRevocationGuard memberGuard = new LicenseRevocationGuard(revocations, grants, operations,
                activations, assignments, profiles);

        log.info("在线客户成员绑定缺少准入端口时失败关闭");
        assertThat(memberGuard.onlineBinding(personWithoutAssignment(), "iam", "user-1")).isEmpty();
    }

    @Test
    void revokedAssignmentFromAnotherGrantCannotLeakThroughMissingSource() {
        fixedAssignment();
        SubjectAssignmentEntity foreign = new SubjectAssignmentEntity("sa-other", "gr-other", 1L,
                "PERSON", "iam", "user-1", "[]");
        when(assignments.findById("sa-other")).thenReturn(Optional.of(foreign));
        revoke(RevocationTargetKind.ASSIGNMENT, "sa-other");
        OnlineAuthorizationRequest request = OnlineAuthorizationRequest.personOrService("prod-1", "gr-1",
                SubjectKind.PERSON, "read", "sa-other", Collections.emptyMap(), Collections.emptyList());
        log.info("缺来源时只对当前授权的资格返回吊销，不泄露其他授权的吊销事实");
        assertThat(guard.onlineRejection(request, null, "user-1"))
                .isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void fixedAssignmentRevocationBlocksFrozenCredentialDelivery() {
        fixedAssignment();
        grant = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "PERSON", "ASSIGNED_SUBJECTS", "proof-test",
                "OFFLINE", null);
        grant.setState("ENABLED");
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant));
        String payload = OfflineCredentialComposer.compose(grant,
                new ProductEntity("prod-1", "iss-1", "product-test", "示例产品", "[\"read\"]", "[]"),
                LicenseTestModels.publishedSnapshot(),
                "{\"kind\":\"PERSON\",\"sourceId\":\"iam\",\"subjectId\":\"user-1\","
                        + "\"assignmentId\":\"sa-1\",\"assignmentGeneration\":1}",
                "cred-1", java.time.Instant.now().getEpochSecond());
        SigningOperationEntity fixed = new SigningOperationEntity("op-1", "cred-1", "gr-1", "sp-1",
                operation.getFrozenHeader(), Base64Url.encode(payload.getBytes(StandardCharsets.UTF_8)), "idem-1");
        guard.requireCredentialUsable(fixed);
        revoke(RevocationTargetKind.ASSIGNMENT, "sa-1");
        log.info("固定资格凭据交付按真实assignment检查吊销");
        assertThatThrownBy(() -> guard.requireCredentialUsable(fixed)).isInstanceOf(LicenseHttpException.class);
    }

    @Test
    void customerMemberCannotUseOfflineCredentialEvenWhenDirectoryAdmitsCaller() {
        SubjectAdmissionService admission = mock(SubjectAdmissionService.class);
        customerMemberCredential(admission);
        when(admission.isAdmitted("cu-1", "user-1")).thenReturn(SubjectAdmissionService.Admission.ADMITTED);
        log.info("客户成员政策禁止离线文件，目录准入不能改变文件模式边界");
        assertThatThrownBy(() -> guard.requireCredentialUsable(operation)).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(409);
    }

    @Test
    void customerMemberOfflineCredentialRejectedBeforeDirectoryDependency() {
        SubjectAdmissionService admission = mock(SubjectAdmissionService.class);
        customerMemberCredential(admission);
        when(admission.isAdmitted("cu-1", "user-1")).thenReturn(SubjectAdmissionService.Admission.UNAVAILABLE);
        log.info("非法客户成员离线文件先被协议拒绝，不把目录故障作为可交付证明");
        assertThatThrownBy(() -> guard.requireCredentialUsable(operation)).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(409);
        verifyNoInteractions(admission);
    }

    @Test
    void readyContentEndpointAppliesGuardAndPreservesAuthorizedBytes() {
        operation.setState("READY");
        operation.setCommittedSignature(Base64Url.encode(new byte[64]));
        when(operations.findByCredentialId("cred-1")).thenReturn(operation);
        LicenseGrantController controller = new LicenseGrantController(grants,
                mock(io.github.surezzzzzz.sdk.license.server.repository.ProductRepository.class), profiles,
                operations, null, null, null, null, guard);
        String response = controller.deliverContent("cred-1", TestPlans.all("deliver"),
                LicenseIdentityHttpTest.TestRequests.authenticated()).getBody();
        log.info("READY交付入口保持冻结原文并执行吊销围栏");
        assertThat(response).contains(operation.getFrozenHeader() + "." + operation.getFrozenPayload());
        revoke(RevocationTargetKind.CREDENTIAL, "cred-1");
        assertThatThrownBy(() -> controller.deliverContent("cred-1", TestPlans.all("deliver"),
                LicenseIdentityHttpTest.TestRequests.authenticated())).isInstanceOf(LicenseHttpException.class);
    }

    @Test
    void revokedBeforeSigningNeverCallsSigner() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<SigningOperationEntity> stored = new AtomicReference<>(operation);
        CredentialIssuanceService service = service(stored, (signingProfileId, input) -> {
            calls.incrementAndGet();
            return new byte[64];
        });
        revoke(RevocationTargetKind.GRANT, "gr-1");
        log.info("签名前已吊销不得发出签名请求");
        assertThatThrownBy(() -> service.signAndCommit("op-1")).isInstanceOf(LicenseHttpException.class);
        assertThat(calls.get()).isZero();
        assertThat(stored.get().getState()).isEqualTo(OperationState.PENDING_SIGN.getCode());
    }

    @Test
    void revokedDuringSigningCannotCommitReadyOrReturnFile() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        StringBuilder hex = new StringBuilder();
        for (byte value : pair.getPublic().getEncoded()) {
            hex.append(String.format("%02x", value));
        }
        profile = new SigningProfileEntity("sp-1", "prod-1", "CREDENTIAL", "kid-test", "key-test", 1,
                hex.toString());
        when(profiles.findById("sp-1")).thenReturn(Optional.of(profile));
        AtomicReference<SigningOperationEntity> stored = new AtomicReference<>(operation);
        CredentialIssuanceService service = service(stored, (signingProfileId, input) -> {
            byte[] signature = Es256Primitives.signForTesting(input.getBytes(), pair.getPrivate());
            revoke(RevocationTargetKind.ACTIVATION, "act-1");
            return signature;
        });
        log.info("签名期间吊销激活，返回结果不得进入READY");
        assertThatThrownBy(() -> service.signAndCommit("op-1")).isInstanceOf(LicenseHttpException.class);
        assertThat(stored.get().getState()).isEqualTo(OperationState.REJECTED.getCode());
        assertThat(stored.get().getCommittedSignature()).isNull();
    }

    @Test
    void guardDependencyFailureAfterSigningIsUncertainAndDoesNotCommitSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        StringBuilder hex = new StringBuilder();
        for (byte value : pair.getPublic().getEncoded()) {
            hex.append(String.format("%02x", value));
        }
        profile = new SigningProfileEntity("sp-1", "prod-1", "CREDENTIAL", "kid-test", "key-test", 1,
                hex.toString());
        when(profiles.findById("sp-1")).thenReturn(Optional.of(profile));
        AtomicReference<SigningOperationEntity> stored = new AtomicReference<>(operation);
        CredentialIssuanceService service = service(stored, (signingProfileId, input) -> {
            byte[] signature = Es256Primitives.signForTesting(input.getBytes(), pair.getPrivate());
            when(grants.findById("gr-1")).thenThrow(
                    new org.springframework.dao.DataAccessResourceFailureException("fixture dependency failure"));
            return signature;
        });
        log.info("签名后本地依赖故障保留UNCERTAIN恢复语义，不能伪装确定吊销");
        assertThatThrownBy(() -> service.signAndCommit("op-1")).isInstanceOf(IllegalStateException.class)
                .hasMessage(RevocationConstant.GUARD_UNAVAILABLE);
        assertThat(stored.get().getState()).isEqualTo(OperationState.UNCERTAIN.getCode());
        assertThat(stored.get().getReasonCategory()).isEqualTo(RevocationConstant.GUARD_UNAVAILABLE_REASON);
        assertThat(stored.get().getCommittedSignature()).isNull();
    }

    private void denyCredential(RevocationTargetKind kind, String targetId) {
        revoke(kind, targetId);
        log.info("凭据执行被吊销围栏阻断：targetKind={}", kind);
        assertThatThrownBy(() -> guard.requireCredentialUsable(operation)).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(409);
    }

    private void revoke(RevocationTargetKind kind, String targetId) {
        when(revocations.findByTargetKindAndTargetId(kind.getCode(), targetId)).thenReturn(Optional.of(
                new RevocationEntity("rv-test", kind.getCode(), targetId, "SECURITY", "iam:admin")));
    }

    private void fixedAssignment() {
        grant = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "PERSON", "ASSIGNED_SUBJECTS", null,
                "ONLINE", null);
        grant.setState("ENABLED");
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant));
        SubjectAssignmentEntity assignment = new SubjectAssignmentEntity("sa-1", "gr-1", 1L, "PERSON",
                "iam", "user-1", "[]");
        when(assignments.findById("sa-1")).thenReturn(Optional.of(assignment));
        when(assignments.findByGrantId("gr-1")).thenReturn(Collections.singletonList(assignment));
    }

    private void customerMemberCredential(SubjectAdmissionService admission) {
        guard = new LicenseRevocationGuard(revocations, grants, operations, activations, assignments, profiles,
                admission);
        // 存量动态成员离线行仍需目录围栏；当前创建入口禁止该组合，不用 HYBRID 冒充离线发行能力。
        grant = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "PERSON", "CUSTOMER_MEMBERS", null,
                "OFFLINE", null);
        grant.setState("ENABLED");
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant));
        String payload = OfflineCredentialComposer.compose(grant,
                new ProductEntity("prod-1", "iss-1", "product-test", "示例产品", "[\"read\"]", "[]"),
                LicenseTestModels.publishedSnapshot(),
                "{\"kind\":\"PERSON\",\"sourceId\":\"iam\",\"subjectId\":\"user-1\"}",
                "cred-1", java.time.Instant.now().getEpochSecond());
        operation = new SigningOperationEntity("op-1", "cred-1", "gr-1", "sp-1", operation.getFrozenHeader(),
                Base64Url.encode(payload.getBytes(StandardCharsets.UTF_8)), "idem-1");
    }

    private OnlineAuthorizationRequest deployment() {
        return OnlineAuthorizationRequest.deployment("prod-1", "gr-1", "read", "act-1",
                Collections.emptyMap(), Collections.emptyList());
    }

    private OnlineAuthorizationRequest person() {
        return OnlineAuthorizationRequest.personOrService("prod-1", "gr-1", SubjectKind.PERSON, "read", "sa-1",
                Collections.emptyMap(), Collections.emptyList());
    }

    private OnlineAuthorizationRequest personWithoutAssignment() {
        return OnlineAuthorizationRequest.personOrService("prod-1", "gr-1", SubjectKind.PERSON, "read", null,
                Collections.emptyMap(), Collections.emptyList());
    }

    private CredentialIssuanceService service(AtomicReference<SigningOperationEntity> stored, LicenseSigner signer) {
        return new CredentialIssuanceService(new OperationRepository() {
            @Override
            public void save(SigningOperationEntity entity) {
                stored.set(entity);
            }

            @Override
            public SigningOperationEntity find(String operationId) {
                return stored.get();
            }

            @Override
            public void update(SigningOperationEntity entity) {
                stored.set(entity);
            }
        }, signer, profiles, guard);
    }
}
