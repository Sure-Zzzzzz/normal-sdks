package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsDestructionJobState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsStateConflictException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionWorkerState;
import io.github.surezzzzzz.sdk.kms.core.repository.*;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import io.github.surezzzzzz.sdk.kms.server.service.DefaultDestructionJobService;
import io.github.surezzzzzz.sdk.kms.server.service.KmsAuditPublisher;
import io.github.surezzzzzz.sdk.kms.server.service.KmsKeyLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 默认销毁任务服务恢复测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultDestructionJobServiceTest {

    /**
     * 构造待处理销毁任务。
     */
    private static KmsDestructionJob job(Instant now) {
        return KmsDestructionJob.builder().tenantId("test-tenant").keyRef("test-key-ref").keyVersion(1)
                .state(KmsDestructionJobState.PENDING).dueAt(now).attemptCount(0).build();
    }

    /**
     * 构造只用于恢复扫描的默认销毁任务服务。
     *
     * @param clock                 数据库权威时钟
     * @param jobRepository         销毁任务仓储
     * @param workerStateRepository worker 状态仓储
     * @return 默认销毁任务服务
     */
    private static DefaultDestructionJobService service(KmsClock clock, KmsDestructionJobRepository jobRepository,
                                                        KmsDestructionWorkerStateRepository workerStateRepository) {
        return service(clock, jobRepository, workerStateRepository, mock(KmsAuditPublisher.class));
    }

    /**
     * 构造带审计发布器的默认销毁任务服务。
     */
    private static DefaultDestructionJobService service(KmsClock clock, KmsDestructionJobRepository jobRepository,
                                                        KmsDestructionWorkerStateRepository workerStateRepository,
                                                        KmsAuditPublisher auditPublisher) {
        SmartKmsServerProperties properties = new SmartKmsServerProperties();
        properties.getWorker().setLeaseSeconds(Long.valueOf(30L));
        properties.getWorker().setMaxConsecutiveFailures(Integer.valueOf(3));
        KmsKeyLock keyLock = mock(KmsKeyLock.class);
        KmsKeyRepository keyRepository = mock(KmsKeyRepository.class);
        KmsKeyVersionRepository keyVersionRepository = mock(KmsKeyVersionRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        return new DefaultDestructionJobService(keyLock, clock, keyRepository, keyVersionRepository, jobRepository,
                workerStateRepository, properties, transactionManager, auditPublisher);
    }

    /**
     * 验证达到失败阈值后仍执行无领取扫描，成功后由状态仓储恢复资格。
     */
    @Test
    void shouldScanWithoutClaimAndRecoverAfterFailureThreshold() {
        KmsClock clock = mock(KmsClock.class);
        KmsDestructionJobRepository jobRepository = mock(KmsDestructionJobRepository.class);
        KmsDestructionWorkerStateRepository workerStateRepository = mock(KmsDestructionWorkerStateRepository.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.now()).thenReturn(now);
        when(workerStateRepository.findByInstanceId("test-kms-worker")).thenReturn(Optional.of(
                KmsDestructionWorkerState.builder().instanceId("test-kms-worker").consecutiveFailureCount(3).build()));
        when(jobRepository.findDueOrExpiredClaim(now)).thenReturn(Collections.singletonList(KmsDestructionJob.builder()
                .tenantId("test-tenant").keyRef("test-key-ref").keyVersion(1)
                .state(KmsDestructionJobState.PENDING).dueAt(now).attemptCount(0).build()));
        DefaultDestructionJobService service = service(clock, jobRepository, workerStateRepository);

        assertDoesNotThrow(() -> service.processDueJobs("test-kms-worker"),
                "达到阈值后的无领取扫描必须可用于恢复 worker 资格");
        log.info("失败阈值后的恢复扫描已完成");
        verify(jobRepository).findDueOrExpiredClaim(now);
        verify(jobRepository, never()).claim(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(),
                anyString(), any(Instant.class), any(Instant.class));
        verify(workerStateRepository).recordSuccess("test-kms-worker", now);
    }

    /**
     * 验证 worker 领取期间的状态冲突按拒绝事件记录。
     */
    @Test
    void shouldAuditStateConflictAsRejected() {
        KmsClock clock = mock(KmsClock.class);
        KmsDestructionJobRepository jobRepository = mock(KmsDestructionJobRepository.class);
        KmsDestructionWorkerStateRepository workerStateRepository = mock(KmsDestructionWorkerStateRepository.class);
        KmsAuditPublisher auditPublisher = mock(KmsAuditPublisher.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        KmsDestructionJob job = job(now);
        when(clock.now()).thenReturn(now);
        when(jobRepository.findDueOrExpiredClaim(now)).thenReturn(Collections.singletonList(job));
        when(jobRepository.claim(eq("test-tenant"), eq("test-key-ref"), eq(1), anyString(), any(Instant.class),
                eq(now))).thenThrow(new KmsStateConflictException());
        DefaultDestructionJobService service = service(clock, jobRepository, workerStateRepository, auditPublisher);

        assertThrows(KmsStateConflictException.class, () -> service.processDueJobs("test-kms-worker"),
                "领取状态冲突必须保留给调用方处理");

        verify(auditPublisher).rejected(any(), eq("test-key-ref"), eq(Integer.valueOf(1)),
                eq(KmsOperation.PROCESS_KEY_DESTRUCTION), eq(SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID),
                eq(SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_STATE_CONFLICT));
        verify(workerStateRepository).recordFailure("test-kms-worker", now);
    }

    /**
     * 验证 worker 领取期间的持久化故障按失败事件记录。
     */
    @Test
    void shouldAuditPersistenceFailureAsFailed() {
        KmsClock clock = mock(KmsClock.class);
        KmsDestructionJobRepository jobRepository = mock(KmsDestructionJobRepository.class);
        KmsDestructionWorkerStateRepository workerStateRepository = mock(KmsDestructionWorkerStateRepository.class);
        KmsAuditPublisher auditPublisher = mock(KmsAuditPublisher.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        KmsDestructionJob job = job(now);
        when(clock.now()).thenReturn(now);
        when(jobRepository.findDueOrExpiredClaim(now)).thenReturn(Collections.singletonList(job));
        when(jobRepository.claim(eq("test-tenant"), eq("test-key-ref"), eq(1), anyString(), any(Instant.class),
                eq(now))).thenThrow(new KmsPersistenceException());
        DefaultDestructionJobService service = service(clock, jobRepository, workerStateRepository, auditPublisher);

        assertThrows(KmsPersistenceException.class, () -> service.processDueJobs("test-kms-worker"),
                "领取持久化故障必须保留给调用方处理");

        verify(auditPublisher).failed(any(), eq("test-key-ref"), eq(Integer.valueOf(1)),
                eq(KmsOperation.PROCESS_KEY_DESTRUCTION), eq(SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID),
                eq(SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_PERSISTENCE));
        verify(workerStateRepository).recordFailure("test-kms-worker", now);
    }
}
