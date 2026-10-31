package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.spi.LicenseSigner;
import io.github.surezzzzzz.sdk.license.server.constant.OperationState;
import io.github.surezzzzzz.sdk.license.server.configuration.LicenseServerProperties;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.CredentialIssuanceService;
import io.github.surezzzzzz.sdk.license.server.service.JpaOperationRepository;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * 真实MySQL签发执行围栏：两实例恢复竞争、原冻结输入重签、迟到工作者与KMS追踪持久化。
 * 密码学使用真实EC测试密钥；业务资格围栏在独立回归中覆盖，本组只隔离状态与持久化。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = PersistenceTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OperationRecoveryPersistenceTest {

    @Autowired
    private SigningOperationRepository operations;
    @Autowired
    private SigningProfileRepository profiles;
    private String operationId;
    private String profileId;
    private KeyPair keys;
    private JpaOperationRepository firstRepository;
    private JpaOperationRepository secondRepository;
    private final LicenseRevocationGuard guard = mock(LicenseRevocationGuard.class);
    private final Set<String> additionalOperations = new HashSet<>();

    @BeforeEach
    void setUp() throws Exception {
        String unique = UUID.randomUUID().toString();
        operationId = "op-recovery-" + unique;
        profileId = "sp-recovery-" + unique;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        keys = generator.generateKeyPair();
        StringBuilder hex = new StringBuilder();
        for (byte value : keys.getPublic().getEncoded()) {
            hex.append(String.format("%02x", value));
        }
        profiles.saveAndFlush(new SigningProfileEntity(profileId, "prod-recovery-" + unique, "CREDENTIAL",
                "kid-recovery-" + unique, "key-recovery-" + unique, 1, hex.toString()));
        firstRepository = new JpaOperationRepository(operations);
        secondRepository = new JpaOperationRepository(operations);
        firstRepository.save(new SigningOperationEntity(operationId, "cred-recovery-" + unique,
                "gr-recovery-" + unique, profileId,
                Base64Url.encode("{\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8)),
                Base64Url.encode("{\"fixture\":true}".getBytes(StandardCharsets.UTF_8)), "idem-recovery-" + unique));
    }

    @AfterEach
    void cleanup() {
        for (String id : additionalOperations) {
            if (operations.existsById(id)) {
                operations.deleteById(id);
            }
        }
        if (operations.existsById(operationId)) {
            operations.deleteById(operationId);
        }
        if (profiles.existsById(profileId)) {
            profiles.deleteById(profileId);
        }
    }

    @Test
    void uncertainTaskReallyRecoversWithOriginalFrozenBytesAndPersistentNewCallReference() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<byte[]> firstInput = new AtomicReference<>();
        AtomicReference<byte[]> recoveredInput = new AtomicReference<>();
        CredentialIssuanceService service = service(firstRepository, (profile, input) -> {
            if (calls.incrementAndGet() == 1) {
                firstInput.set(input.getBytes());
                throw new IllegalStateException("fixture unknown result");
            }
            recoveredInput.set(input.getBytes());
            return Es256Primitives.signForTesting(input.getBytes(), keys.getPrivate());
        });
        assertThatThrownBy(() -> service.signAndCommit(operationId)).isInstanceOf(IllegalStateException.class);
        SigningOperationEntity unknown = firstRepository.find(operationId);
        String previousCall = unknown.getLastKmsCallRef();
        assertThat(unknown.getState()).isEqualTo("UNCERTAIN");
        String result = service.recoverAndCommit(operationId, unknown.getRowVersion());
        SigningOperationEntity ready = secondRepository.find(operationId);
        log.info("真实MySQL恢复：state={}，generation={}，rowVersion={}",
                ready.getState(), ready.getExecutionGeneration(), ready.getRowVersion());
        assertThat(ready.getState()).isEqualTo("READY");
        assertThat(ready.getExecutionGeneration()).isEqualTo(2L);
        assertThat(recoveredInput.get()).isEqualTo(firstInput.get());
        assertThat(ready.getLastKmsCallRef()).startsWith("kms-").isNotEqualTo(previousCall);
        assertThat(ready.getLastKmsCallAt()).isNotNull();
        assertThat(result).endsWith("." + ready.getCommittedSignature());
        assertThatThrownBy(() -> service.recoverAndCommit(operationId, ready.getRowVersion()))
                .isInstanceOf(LicenseHttpException.class);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void twoIndependentRecoveriesClaimOnceBeforeCallingSigner() throws Exception {
        SigningOperationEntity unknown = markUnknown(firstRepository);
        CountDownLatch readSameVersion = new CountDownLatch(2);
        doAnswer(call -> {
            readSameVersion.countDown();
            if (!readSameVersion.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("fixture recovery did not rendezvous");
            }
            return null;
        }).when(guard).requireCredentialUsable(any());
        AtomicInteger calls = new AtomicInteger();
        LicenseSigner signer = (profile, input) -> {
            calls.incrementAndGet();
            return Es256Primitives.signForTesting(input.getBytes(), keys.getPrivate());
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = workers.submit(
                    () -> recover(service(firstRepository, signer), unknown.getRowVersion()));
            Future<Boolean> b = workers.submit(
                    () -> recover(service(secondRepository, signer), unknown.getRowVersion()));
            boolean first = a.get(20, TimeUnit.SECONDS);
            boolean second = b.get(20, TimeUnit.SECONDS);
            log.info("真实MySQL双实例恢复：成功结果={}/{}，签名调用次数={}", first, second, calls.get());
            assertThat(first ^ second).isTrue();
            assertThat(calls.get()).isEqualTo(1);
            assertThat(firstRepository.find(operationId).getState()).isEqualTo("READY");
            assertThat(firstRepository.find(operationId).getExecutionGeneration()).isEqualTo(2L);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void lateOriginalWorkerCannotOverwriteRecoveredSignatureOrTracing() throws Exception {
        CountDownLatch originalEntered = new CountDownLatch(1);
        CountDownLatch releaseOriginal = new CountDownLatch(1);
        CredentialIssuanceService original = service(firstRepository, (profile, input) -> {
            originalEntered.countDown();
            try {
                if (!releaseOriginal.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("fixture release timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return Es256Primitives.signForTesting(input.getBytes(), keys.getPrivate());
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<String> originalResult = worker.submit(() -> original.signAndCommit(operationId));
            assertThat(originalEntered.await(10, TimeUnit.SECONDS)).isTrue();
            SigningOperationEntity lost = markStaleSigning(secondRepository);
            CredentialIssuanceService recovery = service(secondRepository,
                    (profile, input) -> Es256Primitives.signForTesting(input.getBytes(), keys.getPrivate()), 300L);
            recovery.recoverAndCommit(operationId, lost.getRowVersion(), lost.getExecutionGeneration(), "MANUAL_RETRY");
            SigningOperationEntity accepted = secondRepository.find(operationId);
            releaseOriginal.countDown();
            assertThatThrownBy(() -> originalResult.get(15, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
            SigningOperationEntity afterLate = firstRepository.find(operationId);
            log.info("真实MySQL人工接管失联SIGNING，迟到旧代数不得覆盖READY：generation={}",
                    afterLate.getExecutionGeneration());
            assertThat(afterLate.getState()).isEqualTo("READY");
            assertThat(afterLate.getExecutionGeneration()).isEqualTo(2L);
            assertThat(afterLate.getCommittedSignature()).isEqualTo(accepted.getCommittedSignature());
            assertThat(afterLate.getLastKmsCallRef()).isEqualTo(accepted.getLastKmsCallRef());
        } finally {
            releaseOriginal.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void staleVersionAndWrongGenerationNeverOverwritePersistedTask() {
        SigningOperationEntity stale = firstRepository.find(operationId);
        SigningOperationEntity latest = secondRepository.find(operationId);
        latest.setState("UNCERTAIN");
        secondRepository.update(latest);
        stale.setState("READY");
        stale.setCommittedSignature("late-signature");
        log.info("真实MySQL条件更新同时核对rowVersion与执行代数");
        assertThatThrownBy(() -> firstRepository.update(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        SigningOperationEntity sameVersionWrongGeneration = firstRepository.find(operationId);
        assertThatThrownBy(() -> firstRepository.update(sameVersionWrongGeneration, 999L))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(secondRepository.find(operationId).getState()).isEqualTo("UNCERTAIN");
        assertThat(secondRepository.find(operationId).getCommittedSignature()).isNull();
    }

    @Test
    void safeAbandonmentAndStaleRecoveryNeverCallSignerOrReturnToReady() {
        SigningOperationEntity unknown = markUnknown(firstRepository);
        AtomicInteger calls = new AtomicInteger();
        CredentialIssuanceService service = service(firstRepository, (profile, input) -> {
            calls.incrementAndGet();
            return new byte[64];
        });
        service.abandon(operationId, unknown.getRowVersion(), "MANUAL_STOP");
        SigningOperationEntity abandoned = secondRepository.find(operationId);
        log.info("真实MySQL放弃终态与过期恢复请求不调用签名服务");
        assertThat(abandoned.getState()).isEqualTo("ABANDONED");
        assertThat(abandoned.getReasonCategory()).isEqualTo("MANUAL_STOP");
        assertThatThrownBy(() -> service.recoverAndCommit(operationId, unknown.getRowVersion()))
                .isInstanceOf(LicenseHttpException.class);
        assertThatThrownBy(() -> service.recoverAndCommit(operationId, abandoned.getRowVersion()))
                .isInstanceOf(LicenseHttpException.class);
        assertThat(calls.get()).isZero();
    }

    @Test
    void twoIndependentAcceptorsReturnOneTaskAndOnlyOwnerExecutesSigner() throws Exception {
        SigningOperationEntity input = firstRepository.find(operationId);
        String firstId = "op-idempotency-" + UUID.randomUUID();
        String secondId = "op-idempotency-" + UUID.randomUUID();
        additionalOperations.add(firstId);
        additionalOperations.add(secondId);
        String digest = "idem-concurrent-" + UUID.randomUUID();
        CountDownLatch bothPrepared = new CountDownLatch(2);
        doAnswer(call -> {
            bothPrepared.countDown();
            if (!bothPrepared.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("fixture acceptors did not rendezvous");
            }
            return null;
        }).when(guard).requireCredentialUsable(any());
        AtomicInteger signCalls = new AtomicInteger();
        LicenseSigner signer = (profile, signingInput) -> {
            signCalls.incrementAndGet();
            return Es256Primitives.signForTesting(signingInput.getBytes(), keys.getPrivate());
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = workers.submit(() -> acceptThenSignIfOwner(
                    service(firstRepository, signer), firstId, input, digest));
            Future<String> second = workers.submit(() -> acceptThenSignIfOwner(
                    service(secondRepository, signer), secondId, input, digest));
            String winnerA = first.get(20, TimeUnit.SECONDS);
            String winnerB = second.get(20, TimeUnit.SECONDS);
            log.info("真实MySQL并发幂等受理：同任务={}，签名次数={}", winnerA.equals(winnerB), signCalls.get());
            assertThat(winnerA).isEqualTo(winnerB);
            assertThat(operations.existsById(firstId) ^ operations.existsById(secondId)).isTrue();
            assertThat(signCalls.get()).isEqualTo(1);
            assertThat(firstRepository.findByIdempotencyDigest(digest).getState()).isEqualTo("READY");
            assertThat(firstRepository.findByIdempotencyDigest(digest).getOperationId()).isEqualTo(winnerA);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void idempotencyKeysPreserveCaseAndUnrelatedConstraintFailureIsNotReplayed() {
        SigningOperationEntity input = firstRepository.find(operationId);
        CredentialIssuanceService service = service(firstRepository, (profile, signingInput) -> new byte[64]);
        String upperId = "op-case-" + UUID.randomUUID();
        String lowerId = "op-case-" + UUID.randomUUID();
        additionalOperations.add(upperId);
        additionalOperations.add(lowerId);
        String suffix = UUID.randomUUID().toString();
        assertThat(service.accept(upperId, "cred-case-" + UUID.randomUUID(), input.getGrantId(), profileId,
                input.getFrozenHeader(), input.getFrozenPayload(), "ABC-" + suffix)).isEqualTo(upperId);
        assertThat(service.accept(lowerId, "cred-case-" + UUID.randomUUID(), input.getGrantId(), profileId,
                input.getFrozenHeader(), input.getFrozenPayload(), "abc-" + suffix)).isEqualTo(lowerId);
        String collisionId = "op-collision-" + UUID.randomUUID();
        additionalOperations.add(collisionId);
        log.info("真实MySQL幂等键大小写精确，非同键唯一失败原样透传");
        assertThat(firstRepository.findByIdempotencyDigest("ABC-" + suffix).getOperationId()).isEqualTo(upperId);
        assertThat(firstRepository.findByIdempotencyDigest("abc-" + suffix).getOperationId()).isEqualTo(lowerId);
        assertThatThrownBy(() -> service.accept(collisionId, input.getCredentialId(), input.getGrantId(), profileId,
                input.getFrozenHeader(), input.getFrozenPayload(), "idem-other-" + suffix))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(operations.existsById(collisionId)).isFalse();
    }

    @Test
    void staleSigningTakeoverIsDisabledWithoutExplicitHostPolicy() {
        SigningOperationEntity lost = markStaleSigning(firstRepository);
        AtomicInteger calls = new AtomicInteger();
        CredentialIssuanceService service = service(secondRepository, (profile, input) -> {
            calls.incrementAndGet();
            return new byte[64];
        });
        log.info("真实MySQL失联SIGNING无宿主窗口不可接管，原追踪与版本保持");
        assertConflict(() -> service.recoverAndCommit(operationId, lost.getRowVersion(),
                lost.getExecutionGeneration(), "MANUAL_RETRY"));
        SigningOperationEntity unchanged = firstRepository.find(operationId);
        assertThat(unchanged.getState()).isEqualTo("SIGNING");
        assertThat(unchanged.getRowVersion()).isEqualTo(lost.getRowVersion());
        assertThat(unchanged.getLastKmsCallRef()).isEqualTo(lost.getLastKmsCallRef());
        assertThat(calls.get()).isZero();
    }

    @Test
    void takeoverRejectsFreshMissingFutureTimestampAndWrongGenerationWithoutSigning() {
        SigningOperationEntity lost = markStaleSigning(firstRepository);
        AtomicInteger calls = new AtomicInteger();
        CredentialIssuanceService service = service(secondRepository, (profile, input) -> {
            calls.incrementAndGet();
            return new byte[64];
        }, 300L);
        log.info("真实MySQL接管必须已超窗、真实调用时刻且匹配原代数");
        assertConflict(() -> service.recoverAndCommit(operationId, lost.getRowVersion(), 999L, "MANUAL_RETRY"));
        for (java.time.Instant timestamp : new java.time.Instant[] {
                java.time.Instant.now(), null, java.time.Instant.now().plusSeconds(600)}) {
            SigningOperationEntity current = firstRepository.find(operationId);
            current.setLastKmsCallAt(timestamp);
            firstRepository.update(current);
            assertConflict(() -> service.recoverAndCommit(operationId, current.getRowVersion(),
                    current.getExecutionGeneration(), "MANUAL_RETRY"));
        }
        assertThat(firstRepository.find(operationId).getExecutionGeneration()).isEqualTo(1L);
        assertThat(firstRepository.find(operationId).getState()).isEqualTo("SIGNING");
        assertThat(calls.get()).isZero();
    }

    @Test
    void twoIndependentStaleSigningTakeoversClaimOnlyOneNextGeneration() throws Exception {
        SigningOperationEntity lost = markStaleSigning(firstRepository);
        CountDownLatch bothRead = new CountDownLatch(2);
        doAnswer(call -> {
            bothRead.countDown();
            if (!bothRead.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("fixture takeovers did not rendezvous");
            }
            return null;
        }).when(guard).requireCredentialUsable(any());
        AtomicInteger calls = new AtomicInteger();
        LicenseSigner signer = (profile, input) -> {
            calls.incrementAndGet();
            return Es256Primitives.signForTesting(input.getBytes(), keys.getPrivate());
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = workers.submit(() -> takeover(service(firstRepository, signer, 300L), lost));
            Future<Boolean> second = workers.submit(() -> takeover(service(secondRepository, signer, 300L), lost));
            boolean winnerA = first.get(20, TimeUnit.SECONDS);
            boolean winnerB = second.get(20, TimeUnit.SECONDS);
            log.info("真实MySQL双实例失联接管：赢家={}/{}，签名次数={}", winnerA, winnerB, calls.get());
            assertThat(winnerA ^ winnerB).isTrue();
            assertThat(calls.get()).isEqualTo(1);
            SigningOperationEntity ready = firstRepository.find(operationId);
            assertThat(ready.getState()).isEqualTo("READY");
            assertThat(ready.getExecutionGeneration()).isEqualTo(2L);
            assertThat(ready.getLastKmsCallRef()).isNotEqualTo(lost.getLastKmsCallRef());
            assertThat(ready.getFrozenHeader()).isEqualTo(lost.getFrozenHeader());
            assertThat(ready.getFrozenPayload()).isEqualTo(lost.getFrozenPayload());
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String acceptThenSignIfOwner(CredentialIssuanceService service, String ownOperationId,
                                         SigningOperationEntity input, String digest) {
        String accepted = service.accept(ownOperationId, "cred-idempotency-" + UUID.randomUUID(), input.getGrantId(),
                profileId, input.getFrozenHeader(), input.getFrozenPayload(), digest);
        if (ownOperationId.equals(accepted)) {
            service.signAndCommit(accepted);
        }
        return accepted;
    }

    private SigningOperationEntity markUnknown(JpaOperationRepository repository) {
        SigningOperationEntity entity = repository.find(operationId);
        entity.setState(OperationState.UNCERTAIN.getCode());
        repository.update(entity);
        return entity;
    }

    private CredentialIssuanceService service(JpaOperationRepository repository, LicenseSigner signer) {
        return new CredentialIssuanceService(repository, signer, profiles, guard);
    }

    private CredentialIssuanceService service(JpaOperationRepository repository, LicenseSigner signer, long staleSeconds) {
        LicenseServerProperties properties = new LicenseServerProperties();
        properties.setStaleSigningAfterSeconds(staleSeconds);
        return new CredentialIssuanceService(repository, signer, profiles, guard, properties);
    }

    /** 仅测试模拟已流逝窗口，不改变执行代数或把SIGNING伪装成UNCERTAIN。 */
    private SigningOperationEntity markStaleSigning(JpaOperationRepository repository) {
        SigningOperationEntity entity = repository.find(operationId);
        entity.setState("SIGNING");
        entity.setLastKmsCallRef("kms-lost-test-" + UUID.randomUUID());
        entity.setLastKmsCallAt(java.time.Instant.now().minusSeconds(600));
        repository.update(entity);
        return entity;
    }

    private boolean takeover(CredentialIssuanceService service, SigningOperationEntity original) {
        try {
            service.recoverAndCommit(operationId, original.getRowVersion(),
                    original.getExecutionGeneration(), "MANUAL_RETRY");
            return true;
        } catch (LicenseHttpException conflict) {
            assertThat(conflict.getStatus()).isEqualTo(409);
            return false;
        }
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(409);
    }

    private boolean recover(CredentialIssuanceService service, long expectedVersion) {
        try {
            service.recoverAndCommit(operationId, expectedVersion);
            return true;
        } catch (LicenseHttpException conflict) {
            assertThat(conflict.getStatus()).isEqualTo(409);
            return false;
        }
    }
}
