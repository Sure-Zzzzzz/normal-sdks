package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseOperationController;
import io.github.surezzzzzz.sdk.license.server.configuration.LicenseServerProperties;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.CredentialIssuanceService;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.OperationRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 任务HTTP回归：真实恢复调用、DATA+grantId共同传给SQL、严格版本与终态保护。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseOperationControllerTest {

    private final SigningOperationRepository operations = mock(SigningOperationRepository.class);
    private final OperationRepository persistence = mock(OperationRepository.class);
    private final AtomicInteger signCalls = new AtomicInteger();
    private final LicenseServerProperties properties = new LicenseServerProperties();
    private SigningOperationEntity entity;
    private LicenseOperationController controller;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        StringBuilder hex = new StringBuilder();
        for (byte value : pair.getPublic().getEncoded()) {
            hex.append(String.format("%02x", value));
        }
        entity = new SigningOperationEntity("op-1", "cred-1", "gr-1", "sp-1",
                Base64Url.encode("{\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8)),
                Base64Url.encode("{\"fixture\":true}".getBytes(StandardCharsets.UTF_8)), "idem-1");
        entity.setState("UNCERTAIN");
        when(operations.findById("op-1")).thenReturn(Optional.of(entity));
        when(persistence.find("op-1")).thenReturn(entity);
        SigningProfileRepository profiles = mock(SigningProfileRepository.class);
        when(profiles.findById("sp-1")).thenReturn(Optional.of(new SigningProfileEntity(
                "sp-1", "prod-1", "CREDENTIAL", "kid-test", "key-test", 1, hex.toString())));
        CredentialIssuanceService service = new CredentialIssuanceService(persistence, (profile, input) -> {
            signCalls.incrementAndGet();
            return Es256Primitives.signForTesting(input.getBytes(), pair.getPrivate());
        }, profiles, mock(LicenseRevocationGuard.class), properties);
        controller = new LicenseOperationController(operations, service);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recoveryEndpointExecutesFrozenTaskAndReturnsReadyMetadata() {
        String result = controller.recover("op-1", "{\"expectedRowVersion\":0}",
                TestPlans.all("manage"), auth()).getBody();
        log.info("恢复HTTP实际签名完成，返回READY与结果资源而不是仅改状态");
        assertThat(result).contains("\"state\":\"READY\"", "\"executionGeneration\":2", "/content");
        assertThat(signCalls.get()).isEqualTo(1);
        verify(persistence).update(entity, 1L);
        assertStatus(() -> controller.recover("op-1", "{\"expectedRowVersion\":0}",
                TestPlans.all("manage"), auth()), 409);
        assertThat(signCalls.get()).isEqualTo(1);
    }

    @Test
    void outOfDataScopeCannotRecoverOrCallSigner() {
        log.info("任务恢复DATA越界拒绝，签名调用次数0");
        assertStatus(() -> controller.recover("op-1", "{\"expectedRowVersion\":0}",
                TestPlans.restricted("manage", "gr-other"), auth()), 404);
        assertThat(signCalls.get()).isZero();
        verify(persistence, never()).update(any(), eq(1L));
    }

    @Test
    void operationOutsideScopeAndMissingOperationAreBothNotFound() {
        assertStatus(() -> controller.get("op-1", TestPlans.restricted("read", "gr-other"), auth()), 404);
        assertStatus(() -> controller.get("op-missing", TestPlans.restricted("read", "gr-other"), auth()), 404);
    }

    @Test
    void listPreservesGrantFilterInsideRestrictedDataAndTrueTotal() {
        when(operations.findCredentials(eq(Collections.singleton("gr-1")), eq("gr-other"), eq(null),
                any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0L));
        String result = controller.list(auth(), TestPlans.restricted("read", "gr-1"),
                1, 20, "gr-other").getBody();
        log.info("任务列表grantId筛选与DATA共同入SQL，不忽略筛选");
        assertThat(result).contains("\"total\":0", "\"data\":[]");
        verify(operations).findCredentials(eq(Collections.singleton("gr-1")), eq("gr-other"), eq(null),
                any(Pageable.class));
    }

    @Test
    void invalidPageSizeAndVersionTypesRejectWithoutExecution() {
        log.info("非法分页400，版本null/字符串/小数/负数422");
        assertStatus(() -> controller.list(auth(), TestPlans.all("read"), 0, 20, null), 400);
        assertStatus(() -> controller.list(auth(), TestPlans.all("read"), 1, 0, null), 400);
        for (String version : Arrays.asList("null", "\"0\"", "0.5", "-1", "9223372036854775808")) {
            assertStatus(() -> controller.recover("op-1", "{\"expectedRowVersion\":" + version + "}",
                    TestPlans.all("manage"), auth()), 422);
        }
        assertThat(signCalls.get()).isZero();
    }

    @Test
    void staleSigningRecoveryRequiresGenerationReasonAndExplicitHostPolicy() {
        entity.setState("SIGNING");
        entity.setLastKmsCallAt(java.time.Instant.now().minusSeconds(600));
        String input = "{\"expectedRowVersion\":0,\"expectedExecutionGeneration\":1,\"reasonCategory\":\"MANUAL_RETRY\"}";
        log.info("失联SIGNING默认禁用，宿主显式窗口与原代数理由齐全才接管");
        assertStatus(() -> controller.recover("op-1", input, TestPlans.all("manage"), auth()), 409);
        properties.setStaleSigningAfterSeconds(300L);
        assertStatus(() -> controller.recover("op-1", "{\"expectedRowVersion\":0}",
                TestPlans.all("manage"), auth()), 422);
        assertStatus(() -> controller.recover("op-1",
                "{\"expectedRowVersion\":0,\"expectedExecutionGeneration\":1}",
                TestPlans.all("manage"), auth()), 422);
        assertThat(signCalls.get()).isZero();
        String result = controller.recover("op-1", input, TestPlans.all("manage"), auth()).getBody();
        assertThat(result).contains("\"state\":\"READY\"", "\"executionGeneration\":2", "\"lastKmsCallAt\":\"");
        assertThat(signCalls.get()).isEqualTo(1);
    }

    @Test
    void recoveryRejectsInvalidGenerationAndBlankOrOverlongReasonBeforeSigning() {
        log.info("执行代数类型和理由严格校验，非法输入不触发签名");
        for (String generation : Arrays.asList("null", "\"1\"", "1.5", "0", "-1", "9223372036854775808")) {
            assertStatus(() -> controller.recover("op-1",
                    "{\"expectedRowVersion\":0,\"expectedExecutionGeneration\":" + generation + "}",
                    TestPlans.all("manage"), auth()), 422);
        }
        assertStatus(() -> controller.recover("op-1", "{\"expectedRowVersion\":0,\"reasonCategory\":\"   \"}",
                TestPlans.all("manage"), auth()), 422);
        String longReason = String.join("", Collections.nCopies(65, "X"));
        assertStatus(() -> controller.recover("op-1", "{\"expectedRowVersion\":0,\"reasonCategory\":\"" + longReason + "\"}",
                TestPlans.all("manage"), auth()), 422);
        assertThat(signCalls.get()).isZero();
    }

    @Test
    void abandonmentUsesSamePersistenceFenceAndReadyCannotBeAbandoned() {
        String result = controller.abandon("op-1", "{\"expectedRowVersion\":0,\"reasonCategory\":\"MANUAL_STOP\"}",
                TestPlans.all("manage"), auth()).getBody();
        log.info("安全放弃走CAS仓储，READY文件永不退回");
        assertThat(result).contains("\"state\":\"ABANDONED\"", "MANUAL_STOP");
        verify(persistence).update(entity);
        entity.setState("READY");
        assertStatus(() -> controller.abandon("op-1", "{\"expectedRowVersion\":0}",
                TestPlans.all("manage"), auth()), 409);
        assertThat(signCalls.get()).isZero();
    }

    private javax.servlet.http.HttpServletRequest auth() {
        return LicenseIdentityHttpTest.TestRequests.authenticated();
    }

    private void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(status);
    }
}
