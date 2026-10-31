package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseGrantController;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.service.CredentialIssuanceService;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.OperationRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 签发HTTP受理竞争：输家回读首任务原响应，不调用签名服务或重复发布签发成功审计。
 *
 * @author surezzzzzz
 */
@Slf4j
class CredentialIdempotencyControllerTest {

    private final OperationRepository persistence = mock(OperationRepository.class);
    private final SigningOperationRepository operations = mock(SigningOperationRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final AtomicInteger signerCalls = new AtomicInteger();
    private final GrantRepository grants = mock(GrantRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final SigningProfileRepository profiles = mock(SigningProfileRepository.class);
    private final ModelVersionRepository models = mock(ModelVersionRepository.class);
    private LicenseGrantController controller;

    @BeforeEach
    void setUp() {
        LicenseRevocationGuard guard = mock(LicenseRevocationGuard.class);
        GrantRevisionEntity grant = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "DEPLOYMENT",
                "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null, "mv-test", "{}");
        grant.setState("ENABLED");
        when(grants.findById("gr-1")).thenReturn(Optional.of(grant));
        ModelVersionEntity model = new ModelVersionEntity("mv-test", "model-test", "prod-1", 1L,
                LicenseTestModels.publishedSnapshot());
        model.publish();
        when(models.findById("mv-test")).thenReturn(Optional.of(model));
        when(products.findById("prod-1")).thenReturn(Optional.of(new ProductEntity(
                "prod-1", "iss-1", "product-test", "示例产品", "[\"read\"]", "[]")));
        when(profiles.findByProductIdAndPurposeAndStatus("prod-1", "CREDENTIAL", "ACTIVE"))
                .thenReturn(Optional.of(new SigningProfileEntity(
                        "sp-1", "prod-1", "CREDENTIAL", "kid-test", "key-test", 1, "00")));
        CredentialIssuanceService service = new CredentialIssuanceService(persistence, (profile, input) -> {
            signerCalls.incrementAndGet();
            return new byte[64];
        }, profiles, guard);
        controller = new LicenseGrantController(grants, products, profiles, operations, service, models, null,
                publisher, guard);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptanceUniqueLoserReturnsWinnerMetadataWithoutExecutingSignerOrSuccessAudit() {
        SigningOperationEntity winner = new SigningOperationEntity("op-winner", "cred-winner", "gr-1", "sp-1",
                "header", "payload", "key-1:gr-1");
        winner.setState("READY");
        winner.setCommittedSignature("signature");
        winner.setRowVersion(2L);
        when(persistence.findByIdempotencyDigest("key-1:gr-1")).thenReturn(null, winner);
        doThrow(new DataIntegrityViolationException("fixture idempotency collision")).when(persistence).save(any());
        when(operations.findById("op-winner")).thenReturn(Optional.of(winner));
        org.springframework.http.ResponseEntity<String> response = controller.requestCredential("gr-1", body(),
                "key-1", TestPlans.all("issue"), LicenseIdentityHttpTest.TestRequests.authenticated());
        log.info("签发受理竞争输家只返回首任务，签名与成功审计次数0");
        assertThat(response.getStatusCodeValue()).isEqualTo(202);
        assertThat(response.getBody()).contains("op-winner", "cred-winner", "\"rowVersion\":2");
        assertThat(signerCalls.get()).isZero();
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void emptyOrOversizedIdempotencyKeyRejectsBeforePersistence() {
        log.info("幂等键空值或超存储预算必须422，不进入任务受理");
        for (String key : new String[]{"", String.join("", java.util.Collections.nCopies(128, "k"))}) {
            assertThatThrownBy(() -> controller.requestCredential("gr-1", body(), key, TestPlans.all("issue"),
                    LicenseIdentityHttpTest.TestRequests.authenticated())).isInstanceOf(LicenseHttpException.class)
                    .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
        }
        verify(persistence, never()).save(any());
        assertThat(signerCalls.get()).isZero();
    }

    @Test
    void onlineAndHybridCannotIssueOfflineOrReturnLegacyTask() {
        for (String mode : new String[]{"ONLINE", "HYBRID"}) {
            GrantRevisionEntity incompatible = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "DEPLOYMENT",
                    "ALLOCATED_DEPLOYMENTS", null, mode, null, "mv-test", "{}");
            incompatible.setState("ENABLED");
            when(grants.findById("gr-1")).thenReturn(Optional.of(incompatible));
            assertThatThrownBy(() -> controller.requestCredential("gr-1", body(), "key-1", TestPlans.all("issue"),
                    LicenseIdentityHttpTest.TestRequests.authenticated())).isInstanceOf(LicenseHttpException.class)
                    .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
        }
        verify(operations, never()).findByIdempotencyDigest(any());
        verify(persistence, never()).save(any());
        assertThat(signerCalls.get()).isZero();
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void offlineIssuanceSignsOnceAndReturnsMetadataWithoutFullCredential() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        java.security.KeyPair pair = generator.generateKeyPair();
        StringBuilder publicKey = new StringBuilder();
        for (byte value : pair.getPublic().getEncoded()) {
            publicKey.append(String.format("%02x", value));
        }
        SigningProfileEntity profile = new SigningProfileEntity("sp-1", "prod-1", "CREDENTIAL", "kid-test",
                "key-test", 1, publicKey.toString());
        when(profiles.findByProductIdAndPurposeAndStatus("prod-1", "CREDENTIAL", "ACTIVE"))
                .thenReturn(Optional.of(profile));
        when(profiles.findById("sp-1")).thenReturn(Optional.of(profile));
        AtomicReference<SigningOperationEntity> accepted = new AtomicReference<>();
        doAnswer(invocation -> {
            accepted.set(invocation.getArgument(0));
            return null;
        }).when(persistence).save(any());
        doAnswer(invocation -> {
            accepted.set(invocation.getArgument(0));
            return null;
        }).when(persistence).update(any());
        when(persistence.find(anyString())).thenAnswer(invocation -> accepted.get());
        when(operations.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(accepted.get()));
        LicenseRevocationGuard guard = mock(LicenseRevocationGuard.class);
        CredentialIssuanceService issuance = new CredentialIssuanceService(persistence, (profileId, input) -> {
            signerCalls.incrementAndGet();
            return Es256Primitives.signForTesting(input.getBytes(), pair.getPrivate());
        }, profiles, guard);
        controller = new LicenseGrantController(grants, products, profiles, operations, issuance, models, null,
                publisher, guard);
        org.springframework.http.ResponseEntity<String> response = controller.requestCredential("gr-1", body(),
                "key-success", TestPlans.all("issue"), LicenseIdentityHttpTest.TestRequests.authenticated());
        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        assertThat(response.getBody()).contains("\"state\":\"READY\"")
                .doesNotContain("compactJws", "frozenHeader", "frozenPayload", "committedSignature");
        assertThat(accepted.get().getState()).isEqualTo("READY");
        assertThat(signerCalls.get()).isEqualTo(1);
        verify(publisher).publishEvent(any(Object.class));
    }

    private String body() {
        return "{\"kid\":\"kid-test\",\"subjectRef\":{\"kind\":\"DEPLOYMENT\","
                + "\"activationId\":\"act-1\",\"activationGeneration\":1}}";
    }
}
