package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsNotFoundException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPolicyConflictException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyPolicyRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyVersionRepository;
import io.github.surezzzzzz.sdk.kms.core.service.KeyPolicyManagementService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 默认精确 allow-only 密钥策略管理服务。
 *
 * @author surezzzzzz
 */
public class DefaultKeyPolicyManagementService implements KeyPolicyManagementService {

    private final KmsKeyLock keyLock;
    private final KmsClock clock;
    private final KmsKeyRepository keyRepository;
    private final KmsKeyVersionRepository keyVersionRepository;
    private final KmsKeyPolicyRepository keyPolicyRepository;
    /**
     * 成功策略管理审计发布器。
     */
    private final KmsAuditPublisher auditPublisher;

    /**
     * 创建默认策略管理服务。
     */
    public DefaultKeyPolicyManagementService(KmsKeyLock keyLock, KmsClock clock, KmsKeyRepository keyRepository,
                                             KmsKeyVersionRepository keyVersionRepository,
                                             KmsKeyPolicyRepository keyPolicyRepository,
                                             KmsAuditPublisher auditPublisher) {
        this.keyLock = keyLock;
        this.clock = clock;
        this.keyRepository = keyRepository;
        this.keyVersionRepository = keyVersionRepository;
        this.keyPolicyRepository = keyPolicyRepository;
        this.auditPublisher = auditPublisher;
    }

    /**
     * 校验管理主体和请求标识。
     */
    private static void requireManage(KmsPrincipal principal, String requestId) {
        if (principal == null) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireRequestId(requestId);
        if (!principal.hasScope(SmartKmsServerConstant.SCOPE_MANAGE)) {
            throw new KmsAuthorizationException();
        }
    }

    /**
     * 创建精确策略。
     */
    @Override
    @Transactional
    public KmsKeyPolicy create(KmsPrincipal principal, KmsKeyPolicy policy, String idempotencyKey, String requestId) {
        requireManage(principal, requestId);
        if (policy == null || !principal.getTenantId().equals(policy.getTenantId())) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireIdempotencyKey(idempotencyKey);
        if (policy.getExpiresAt() != null && !policy.getExpiresAt().isAfter(clock.now())) {
            throw new KmsValidationException();
        }
        if (!keyLock.lock(principal.getTenantId(), policy.getKeyRef())) {
            throw new KmsNotFoundException();
        }
        if (!keyRepository.findByKeyRef(principal.getTenantId(), policy.getKeyRef()).isPresent()
                || (policy.getKeyVersion() != null && !keyVersionRepository.findByVersion(principal.getTenantId(),
                policy.getKeyRef(), policy.getKeyVersion().intValue()).isPresent())) {
            throw new KmsNotFoundException();
        }
        KmsKeyPolicy generatedPolicy = KmsKeyPolicy.builder().policyId(UUID.randomUUID().toString())
                .tenantId(principal.getTenantId()).keyRef(policy.getKeyRef()).principalId(policy.getPrincipalId())
                .keyVersion(policy.getKeyVersion()).operation(policy.getOperation()).expiresAt(policy.getExpiresAt())
                .rowVersion(0L).build();
        try {
            KmsKeyPolicy savedPolicy = keyPolicyRepository.save(principal.getTenantId(), generatedPolicy);
            auditPublisher.allowed(principal, savedPolicy.getKeyRef(), savedPolicy.getKeyVersion(),
                    io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.CREATE_KEY_POLICY, requestId,
                    io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_POLICY,
                    null, null, null, null);
            return savedPolicy;
        } catch (io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException exception) {
            throw new KmsPolicyConflictException();
        }
    }

    /**
     * 查询当前 tenant 下的全部策略。
     */
    @Override
    @Transactional(readOnly = true)
    public List<KmsKeyPolicy> list(KmsPrincipal principal, String keyRef, String requestId) {
        requireManage(principal, requestId);
        KmsValidationHelper.requireKeyRef(keyRef);
        if (!keyRepository.findByKeyRef(principal.getTenantId(), keyRef).isPresent()) {
            throw new KmsNotFoundException();
        }
        return keyPolicyRepository.findByKeyRef(principal.getTenantId(), keyRef);
    }

    /**
     * 与密码学授权共享同一 key 行锁后撤销策略。
     */
    @Override
    @Transactional
    public void revoke(KmsPrincipal principal, String keyRef, String policyId, long expectedRowVersion,
                       String idempotencyKey, String requestId) {
        requireManage(principal, requestId);
        KmsValidationHelper.requireKeyRef(keyRef);
        KmsValidationHelper.requirePolicyId(policyId);
        KmsValidationHelper.requireIdempotencyKey(idempotencyKey);
        if (!keyLock.lock(principal.getTenantId(), keyRef)) {
            throw new KmsNotFoundException();
        }
        try {
            keyPolicyRepository.revoke(principal.getTenantId(), keyRef, policyId, expectedRowVersion);
            auditPublisher.allowed(principal, keyRef, null,
                    io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation.REVOKE_KEY_POLICY, requestId,
                    io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_POLICY,
                    null, null, null, null);
        } catch (io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException exception) {
            throw new KmsPolicyConflictException();
        }
    }
}
