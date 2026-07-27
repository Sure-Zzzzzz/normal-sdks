package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.*;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionWorkerState;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.repository.*;
import io.github.surezzzzzz.sdk.kms.core.service.DestructionJobService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 默认销毁任务处理服务。
 *
 * @author surezzzzzz
 */
public class DefaultDestructionJobService implements DestructionJobService {

    private final KmsKeyLock keyLock;
    private final KmsClock clock;
    private final KmsKeyRepository keyRepository;
    private final KmsKeyVersionRepository keyVersionRepository;
    private final KmsDestructionJobRepository destructionJobRepository;
    private final KmsDestructionWorkerStateRepository workerStateRepository;
    private final SmartKmsServerProperties properties;
    /**
     * 保证最终销毁写入使用独立数据库事务的模板。
     */
    private final TransactionTemplate transactionTemplate;
    /**
     * 销毁成功审计发布器。
     */
    private final KmsAuditPublisher auditPublisher;

    /**
     * 创建默认销毁任务服务。
     */
    public DefaultDestructionJobService(KmsKeyLock keyLock, KmsClock clock, KmsKeyRepository keyRepository,
                                        KmsKeyVersionRepository keyVersionRepository,
                                        KmsDestructionJobRepository destructionJobRepository,
                                        KmsDestructionWorkerStateRepository workerStateRepository,
                                        SmartKmsServerProperties properties,
                                        PlatformTransactionManager transactionManager,
                                        KmsAuditPublisher auditPublisher) {
        this.keyLock = keyLock;
        this.clock = clock;
        this.keyRepository = keyRepository;
        this.keyVersionRepository = keyVersionRepository;
        this.destructionJobRepository = destructionJobRepository;
        this.workerStateRepository = workerStateRepository;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.auditPublisher = auditPublisher;
    }

    /**
     * 构造销毁 worker 使用的系统审计主体。
     */
    private static io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal systemPrincipal(KmsDestructionJob job) {
        return new io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal(
                io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID,
                job.getTenantId(), null);
    }

    /**
     * 扫描销毁任务；连续失败达到阈值时仅停止本轮领取，成功扫描后恢复领取资格。
     */
    @Override
    public void processDueJobs(String instanceId) {
        KmsValidationHelper.requireText(instanceId, SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH);
        boolean claimable = isClaimable(instanceId);
        try {
            Instant now = clock.now();
            List<KmsDestructionJob> jobs = destructionJobRepository.findDueOrExpiredClaim(now);
            if (claimable) {
                for (KmsDestructionJob job : jobs) {
                    processCandidate(job, now);
                }
            }
            workerStateRepository.recordSuccess(instanceId, clock.now());
        } catch (RuntimeException exception) {
            workerStateRepository.recordFailure(instanceId, clock.now());
            throw exception;
        }
    }

    /**
     * 按租约 CAS 领取候选任务并处理。
     */
    private void processCandidate(KmsDestructionJob job, Instant now) {
        String claimToken = UUID.randomUUID().toString();
        Instant claimUntil = now.plusSeconds(properties.getWorker().getLeaseSeconds().longValue());
        try {
            if (destructionJobRepository.claim(job.getTenantId(), job.getKeyRef(), job.getKeyVersion(), claimToken,
                    claimUntil, now)) {
                transactionTemplate.executeWithoutResult(status -> destroyClaimed(job, claimToken));
            }
        } catch (RuntimeException exception) {
            auditFailure(job, exception);
            throw exception;
        }
    }

    /**
     * 按安全分类尽力记录 worker 任务处理拒绝或失败。
     */
    private void auditFailure(KmsDestructionJob job, RuntimeException exception) {
        if (exception instanceof KmsValidationException || exception instanceof KmsStateConflictException) {
            auditPublisher.rejected(systemPrincipal(job), job.getKeyRef(), Integer.valueOf(job.getKeyVersion()),
                    io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.PROCESS_KEY_DESTRUCTION,
                    SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID,
                    exception instanceof KmsValidationException ? SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION
                            : SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_STATE_CONFLICT);
        } else if (exception instanceof KmsCryptoException) {
            auditPublisher.failed(systemPrincipal(job), job.getKeyRef(), Integer.valueOf(job.getKeyVersion()),
                    io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.PROCESS_KEY_DESTRUCTION,
                    SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC);
        } else if (exception instanceof KmsPersistenceException) {
            auditPublisher.failed(systemPrincipal(job), job.getKeyRef(), Integer.valueOf(job.getKeyVersion()),
                    io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.PROCESS_KEY_DESTRUCTION,
                    SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_PERSISTENCE);
        } else if (exception instanceof KmsServiceUnavailableException) {
            auditPublisher.failed(systemPrincipal(job), job.getKeyRef(), Integer.valueOf(job.getKeyVersion()),
                    io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.PROCESS_KEY_DESTRUCTION,
                    SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
        } else {
            auditPublisher.failed(systemPrincipal(job), job.getKeyRef(), Integer.valueOf(job.getKeyVersion()),
                    io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.PROCESS_KEY_DESTRUCTION,
                    SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 在同一 key 锁和领取令牌边界内销毁材料并完成任务。
     */
    private void destroyClaimed(KmsDestructionJob job, String claimToken) {
        if (!keyLock.lock(job.getTenantId(), job.getKeyRef())) {
            release(job, claimToken);
            return;
        }
        KmsKey key = keyRepository.findByKeyRef(job.getTenantId(), job.getKeyRef()).orElse(null);
        KmsKeyVersion version = keyVersionRepository.findByVersion(job.getTenantId(), job.getKeyRef(),
                job.getKeyVersion()).orElse(null);
        if (key == null || version == null || key.getState() != KmsKeyState.PENDING_DESTRUCTION
                || version.getState() != KmsKeyVersionState.PENDING_DESTRUCTION) {
            release(job, claimToken);
            return;
        }
        Instant completedAt = clock.now();
        KmsKeyVersion destroyed = new KmsKeyVersion(version.getTenantId(), version.getKeyRef(), version.getVersion(),
                version.getAlgorithm(), KmsKeyVersionState.DESTROYED, null, null, null, null, completedAt);
        keyVersionRepository.save(job.getTenantId(), destroyed);
        if (!destructionJobRepository.complete(job.getTenantId(), job.getKeyRef(), job.getKeyVersion(), claimToken,
                completedAt)) {
            throw new KmsPersistenceException();
        }
        auditPublisher.allowed(systemPrincipal(job), job.getKeyRef(), Integer.valueOf(job.getKeyVersion()),
                io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.PROCESS_KEY_DESTRUCTION,
                io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_SYSTEM_PRINCIPAL_ID,
                io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_DESTRUCTION_JOB,
                key.getState(), KmsKeyVersionState.DESTROYED, null, null);
        if (allVersionsDestroyed(job.getTenantId(), job.getKeyRef())) {
            keyRepository.save(job.getTenantId(), KmsKey.builder().tenantId(key.getTenantId()).keyRef(key.getKeyRef())
                    .keyAlias(key.getKeyAlias()).purpose(key.getPurpose()).algorithm(key.getAlgorithm())
                    .state(KmsKeyState.DESTROYED).activeVersion(null).rowVersion(key.getRowVersion()).build());
        }
    }

    /**
     * 未完成任务不作失败或取消标记，只释放当前令牌。
     */
    private void release(KmsDestructionJob job, String claimToken) {
        destructionJobRepository.release(job.getTenantId(), job.getKeyRef(), job.getKeyVersion(), claimToken);
    }

    /**
     * 判断逻辑密钥的材料版本是否均已销毁。
     */
    private boolean allVersionsDestroyed(String tenantId, String keyRef) {
        for (KmsKeyVersion version : keyVersionRepository.findByKeyRef(tenantId, keyRef)) {
            if (version.getState() != KmsKeyVersionState.DESTROYED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 达到持久化连续失败阈值后停止领取，成功扫描才恢复。
     */
    private boolean isClaimable(String instanceId) {
        KmsDestructionWorkerState state = workerStateRepository.findByInstanceId(instanceId).orElse(null);
        return state == null || state.getConsecutiveFailureCount()
                < properties.getWorker().getMaxConsecutiveFailures().intValue();
    }
}
