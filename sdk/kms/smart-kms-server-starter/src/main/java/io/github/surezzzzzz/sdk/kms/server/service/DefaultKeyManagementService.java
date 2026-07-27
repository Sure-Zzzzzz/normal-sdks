package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsNotFoundException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsStateConflictException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsDestructionJobRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyVersionRepository;
import io.github.surezzzzzz.sdk.kms.core.service.KeyManagementService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsStateHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsDestructionCancellationGuard;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyQueryRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 默认逻辑密钥与版本生命周期管理服务。
 *
 * @author surezzzzzz
 */
public class DefaultKeyManagementService implements KeyManagementService {

    private final KmsKeyLock keyLock;
    private final KmsClock clock;
    private final KmsKeyRepository keyRepository;
    private final KmsKeyQueryRepository keyQueryRepository;
    private final KmsKeyVersionRepository keyVersionRepository;
    private final KmsDestructionJobRepository destructionJobRepository;
    private final KmsDestructionCancellationGuard destructionCancellationGuard;
    private final KmsKeyMaterialGenerator keyMaterialGenerator;
    /**
     * 成功管理操作审计发布器。
     */
    private final KmsAuditPublisher auditPublisher;

    /**
     * 创建默认密钥管理服务。
     */
    public DefaultKeyManagementService(KmsKeyLock keyLock, KmsClock clock, KmsKeyRepository keyRepository,
                                       KmsKeyQueryRepository keyQueryRepository,
                                       KmsKeyVersionRepository keyVersionRepository,
                                       KmsDestructionJobRepository destructionJobRepository,
                                       KmsDestructionCancellationGuard destructionCancellationGuard,
                                       KmsKeyMaterialGenerator keyMaterialGenerator,
                                       KmsAuditPublisher auditPublisher) {
        this.keyLock = keyLock;
        this.clock = clock;
        this.keyRepository = keyRepository;
        this.keyQueryRepository = keyQueryRepository;
        this.keyVersionRepository = keyVersionRepository;
        this.destructionJobRepository = destructionJobRepository;
        this.destructionCancellationGuard = destructionCancellationGuard;
        this.keyMaterialGenerator = keyMaterialGenerator;
        this.auditPublisher = auditPublisher;
    }

    private static KmsKey copyKey(KmsKey source, KmsKeyState state, KmsKeyState stateBeforeDestruction,
                                  Integer activeVersion) {
        return KmsKey.builder().tenantId(source.getTenantId()).keyRef(source.getKeyRef())
                .keyAlias(source.getKeyAlias()).purpose(source.getPurpose()).algorithm(source.getAlgorithm())
                .state(state).stateBeforeDestruction(stateBeforeDestruction).activeVersion(activeVersion)
                .rowVersion(source.getRowVersion()).build();
    }

    private static void requireManage(KmsPrincipal principal, String idempotencyKey, String requestId) {
        requireManageRead(principal, requestId);
        KmsValidationHelper.requireIdempotencyKey(idempotencyKey);
    }

    private static void requireManageRead(KmsPrincipal principal, String requestId) {
        if (principal == null) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireRequestId(requestId);
        if (!principal.hasScope(SmartKmsServerConstant.SCOPE_MANAGE)) {
            throw new KmsAuthorizationException();
        }
    }

    /**
     * 创建逻辑密钥和首个活动版本。
     */
    @Override
    @Transactional
    public KmsKey create(KmsPrincipal principal, KmsKey key, String idempotencyKey, String requestId) {
        requireManage(principal, idempotencyKey, requestId);
        if (key == null || !principal.getTenantId().equals(key.getTenantId()) || key.getState() != KmsKeyState.ACTIVE
                || key.getActiveVersion() == null || key.getActiveVersion().intValue() != 1) {
            throw new KmsValidationException();
        }
        KmsKey generatedKey = KmsKey.builder().tenantId(principal.getTenantId()).keyRef(UUID.randomUUID().toString())
                .keyAlias(key.getKeyAlias()).purpose(key.getPurpose()).algorithm(key.getAlgorithm())
                .state(KmsKeyState.ACTIVE).activeVersion(1).rowVersion(0L).build();
        KmsKey savedKey = keyRepository.save(principal.getTenantId(), generatedKey);
        keyVersionRepository.save(principal.getTenantId(), keyMaterialGenerator.generate(principal.getTenantId(),
                savedKey.getKeyRef(), savedKey.getActiveVersion().intValue(), savedKey.getAlgorithm()));
        auditPublisher.allowed(principal, savedKey.getKeyRef(), null,
                io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.CREATE_KEY, requestId,
                io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY,
                savedKey.getState(), null, null, null);
        return savedKey;
    }

    /**
     * 查询当前 tenant 下逻辑密钥。
     */
    @Override
    @Transactional(readOnly = true)
    public KmsKey find(KmsPrincipal principal, String keyRef, String requestId) {
        requireManageRead(principal, requestId);
        KmsValidationHelper.requireKeyRef(keyRef);
        return keyRepository.findByKeyRef(principal.getTenantId(), keyRef).orElseThrow(KmsNotFoundException::new);
    }

    /**
     * 查询当前 tenant 下无材料逻辑密钥元数据。
     */
    @Override
    @Transactional(readOnly = true)
    public List<KmsKey> list(KmsPrincipal principal, String requestId) {
        requireManageRead(principal, requestId);
        List<KmsKey> result = new ArrayList<KmsKey>();
        for (io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyMetadata metadata
                : keyQueryRepository.findAllMetadata(principal.getTenantId())) {
            result.add(metadata.getKey());
        }
        return result;
    }

    /**
     * 在同一 key 锁内轮换版本并退役旧版本。
     */
    @Override
    @Transactional
    public KmsKey rotate(KmsPrincipal principal, String keyRef, long expectedRowVersion,
                         String idempotencyKey, String requestId) {
        requireManage(principal, idempotencyKey, requestId);
        KmsKey key = lockAndLoad(principal, keyRef, expectedRowVersion);
        if (key.getState() != KmsKeyState.ACTIVE || key.getActiveVersion() == null) {
            throw new KmsStateConflictException();
        }
        KmsKeyVersion current = keyVersionRepository.findByVersion(principal.getTenantId(), keyRef,
                key.getActiveVersion().intValue()).orElseThrow(KmsStateConflictException::new);
        KmsKeyVersion retired = new KmsKeyVersion(current.getTenantId(), current.getKeyRef(), current.getVersion(),
                current.getAlgorithm(), KmsKeyVersionState.RETIRED, null, current.getPrivateMaterial(),
                current.getSymmetricMaterial(), current.getPublicMaterial(), null);
        keyVersionRepository.save(principal.getTenantId(), retired);
        int nextVersion = current.getVersion() + 1;
        keyVersionRepository.save(principal.getTenantId(), keyMaterialGenerator.generate(principal.getTenantId(),
                keyRef, nextVersion, key.getAlgorithm()));
        KmsKey rotated = keyRepository.save(principal.getTenantId(), copyKey(key, key.getState(), null, nextVersion));
        auditPublisher.allowed(principal, keyRef, Integer.valueOf(nextVersion),
                io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.ROTATE_KEY, requestId,
                io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION,
                rotated.getState(), KmsKeyVersionState.ACTIVE, null, null);
        return rotated;
    }

    /**
     * 执行活动与禁用状态之间的合法迁移。
     */
    @Override
    @Transactional
    public KmsKey changeState(KmsPrincipal principal, String keyRef, KmsKeyState targetState,
                              long expectedRowVersion, String idempotencyKey, String requestId) {
        requireManage(principal, idempotencyKey, requestId);
        KmsKey key = lockAndLoad(principal, keyRef, expectedRowVersion);
        if (targetState == null || !KmsStateHelper.canTransition(key.getState(), targetState)
                || targetState == KmsKeyState.PENDING_DESTRUCTION) {
            throw new KmsStateConflictException();
        }
        KmsKey changed = keyRepository.save(principal.getTenantId(),
                copyKey(key, targetState, null, key.getActiveVersion()));
        auditPublisher.allowed(principal, keyRef, null,
                io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.CHANGE_KEY_STATE, requestId,
                io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY,
                changed.getState(), null, null, null);
        return changed;
    }

    /**
     * 为全部未销毁版本安排销毁任务并记录原状态。
     */
    @Override
    @Transactional
    public KmsKey scheduleDestruction(KmsPrincipal principal, String keyRef, Instant dueAt, long expectedRowVersion,
                                      String idempotencyKey, String requestId) {
        requireManage(principal, idempotencyKey, requestId);
        if (dueAt == null || !dueAt.isAfter(clock.now())) {
            throw new KmsValidationException();
        }
        KmsKey key = lockAndLoad(principal, keyRef, expectedRowVersion);
        if (key.getState() != KmsKeyState.ACTIVE && key.getState() != KmsKeyState.DISABLED) {
            throw new KmsStateConflictException();
        }
        for (KmsKeyVersion version : keyVersionRepository.findByKeyRef(principal.getTenantId(), keyRef)) {
            if (version.getState() == KmsKeyVersionState.DESTROYED) {
                continue;
            }
            if (version.getState() != KmsKeyVersionState.ACTIVE && version.getState() != KmsKeyVersionState.RETIRED) {
                throw new KmsStateConflictException();
            }
            KmsKeyVersion pending = new KmsKeyVersion(version.getTenantId(), version.getKeyRef(), version.getVersion(),
                    version.getAlgorithm(), KmsKeyVersionState.PENDING_DESTRUCTION, version.getState(),
                    version.getPrivateMaterial(), version.getSymmetricMaterial(), version.getPublicMaterial(), null);
            keyVersionRepository.save(principal.getTenantId(), pending);
            destructionJobRepository.save(principal.getTenantId(), KmsDestructionJob.builder()
                    .tenantId(principal.getTenantId()).keyRef(keyRef).keyVersion(version.getVersion())
                    .state(io.github.surezzzzzz.sdk.kms.core.constant.KmsDestructionJobState.PENDING).dueAt(dueAt)
                    .attemptCount(0).build());
        }
        KmsKey scheduled = keyRepository.save(principal.getTenantId(), copyKey(key, KmsKeyState.PENDING_DESTRUCTION,
                key.getState(), key.getActiveVersion()));
        auditPublisher.allowed(principal, keyRef, null,
                io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.SCHEDULE_KEY_DESTRUCTION, requestId,
                io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY,
                scheduled.getState(), null, null, null);
        return scheduled;
    }

    /**
     * 在未发生历史领取时恢复所有排程前状态。
     */
    @Override
    @Transactional
    public KmsKey cancelDestruction(KmsPrincipal principal, String keyRef, long expectedRowVersion,
                                    String idempotencyKey, String requestId) {
        requireManage(principal, idempotencyKey, requestId);
        KmsKey key = lockAndLoad(principal, keyRef, expectedRowVersion);
        if (key.getState() != KmsKeyState.PENDING_DESTRUCTION
                || !destructionCancellationGuard.areAllJobsUnclaimed(principal.getTenantId(), keyRef)) {
            throw new KmsStateConflictException();
        }
        destructionCancellationGuard.deleteUnclaimedJobs(principal.getTenantId(), keyRef);
        for (KmsKeyVersion version : keyVersionRepository.findByKeyRef(principal.getTenantId(), keyRef)) {
            if (version.getState() != KmsKeyVersionState.PENDING_DESTRUCTION
                    || version.getStateBeforeDestruction() == null) {
                throw new KmsStateConflictException();
            }
            keyVersionRepository.save(principal.getTenantId(), new KmsKeyVersion(version.getTenantId(),
                    version.getKeyRef(), version.getVersion(), version.getAlgorithm(),
                    version.getStateBeforeDestruction(), null, version.getPrivateMaterial(),
                    version.getSymmetricMaterial(), version.getPublicMaterial(), null));
        }
        KmsKey canceled = keyRepository.save(principal.getTenantId(), copyKey(key, key.getStateBeforeDestruction(),
                null, key.getActiveVersion()));
        auditPublisher.allowed(principal, keyRef, null,
                io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.CANCEL_KEY_DESTRUCTION, requestId,
                io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY,
                canceled.getState(), null, null, null);
        return canceled;
    }

    private KmsKey lockAndLoad(KmsPrincipal principal, String keyRef, long expectedRowVersion) {
        KmsValidationHelper.requireKeyRef(keyRef);
        if (expectedRowVersion < 0 || !keyLock.lock(principal.getTenantId(), keyRef)) {
            throw new KmsNotFoundException();
        }
        KmsKey key = keyRepository.findByKeyRef(principal.getTenantId(), keyRef).orElseThrow(KmsNotFoundException::new);
        if (key.getRowVersion() != expectedRowVersion) {
            throw new KmsStateConflictException();
        }
        return key;
    }
}
