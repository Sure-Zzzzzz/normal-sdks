package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.*;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.service.CryptoOperationService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import org.springframework.transaction.annotation.Transactional;

/**
 * 默认 REST 签名结果服务。
 *
 * @author surezzzzzz
 */
public class DefaultKmsSignatureOperationService implements KmsSignatureOperationService {

    /**
     * 逻辑密钥事务锁。
     */
    private final KmsKeyLock keyLock;
    /**
     * 逻辑密钥仓储。
     */
    private final KmsKeyRepository keyRepository;
    /**
     * 通用密码学操作服务。
     */
    private final CryptoOperationService cryptoOperationService;
    /**
     * 缺省版本选择失败审计发布器。
     */
    private final KmsAuditPublisher auditPublisher;

    /**
     * 创建默认 REST 签名结果服务。
     *
     * @param keyLock                逻辑密钥事务锁
     * @param keyRepository          逻辑密钥仓储
     * @param cryptoOperationService 通用密码学操作服务
     * @param auditPublisher         缺省版本选择失败审计发布器
     */
    public DefaultKmsSignatureOperationService(KmsKeyLock keyLock, KmsKeyRepository keyRepository,
                                               CryptoOperationService cryptoOperationService,
                                               KmsAuditPublisher auditPublisher) {
        this.keyLock = keyLock;
        this.keyRepository = keyRepository;
        this.cryptoOperationService = cryptoOperationService;
        this.auditPublisher = auditPublisher;
    }

    /**
     * 在单一锁定视图中选择默认版本并完成签名。
     */
    @Override
    @Transactional
    public KmsSignatureOperationResult sign(KmsPrincipal principal, String keyRef, Integer version, byte[] input,
                                            String requestId) {
        validateForSignatureAdapter(principal, keyRef, version, requestId);
        if (version != null) {
            byte[] signature = cryptoOperationService.sign(principal, keyRef, version, input, requestId);
            return new KmsSignatureOperationResult(version.intValue(), signature);
        }
        int resolvedVersion = resolveActiveVersion(principal, keyRef, requestId);
        byte[] signature = cryptoOperationService.sign(principal, keyRef, Integer.valueOf(resolvedVersion), input,
                requestId);
        return new KmsSignatureOperationResult(resolvedVersion, signature);
    }

    /**
     * 选择缺省活动版本；选择失败尚未进入通用密码服务时由本层审计。
     */
    private int resolveActiveVersion(KmsPrincipal principal, String keyRef, String requestId) {
        try {
            return activeVersion(principal, keyRef);
        } catch (RuntimeException exception) {
            auditFailure(principal, keyRef, requestId, exception);
            throw exception;
        }
    }

    /**
     * 按安全分类记录缺省版本选择阶段的失败。
     */
    private void auditFailure(KmsPrincipal principal, String keyRef, String requestId, RuntimeException exception) {
        if (exception instanceof KmsValidationException) {
            auditPublisher.rejected(principal, keyRef, null, KmsOperation.SIGN, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION);
        } else if (exception instanceof KmsAuthorizationException) {
            auditPublisher.rejected(principal, keyRef, null, KmsOperation.SIGN, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_AUTHORIZATION);
        } else if (exception instanceof KmsStateConflictException) {
            auditPublisher.rejected(principal, keyRef, null, KmsOperation.SIGN, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_STATE_CONFLICT);
        } else if (exception instanceof KmsCryptoException) {
            auditPublisher.failed(principal, keyRef, null, KmsOperation.SIGN, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC);
        } else if (exception instanceof KmsPersistenceException) {
            auditPublisher.failed(principal, keyRef, null, KmsOperation.SIGN, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_PERSISTENCE);
        } else if (exception instanceof KmsServiceUnavailableException) {
            auditPublisher.failed(principal, keyRef, null, KmsOperation.SIGN, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
        } else {
            auditPublisher.failed(principal, keyRef, null, KmsOperation.SIGN, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 在当前事务的逻辑密钥锁内读取活动版本。
     */
    private int activeVersion(KmsPrincipal principal, String keyRef) {
        if (!keyLock.lock(principal.getTenantId(), keyRef)) {
            throw new KmsAuthorizationException();
        }
        KmsKey key = keyRepository.findByKeyRef(principal.getTenantId(), keyRef)
                .orElseThrow(KmsAuthorizationException::new);
        if (key.getActiveVersion() == null) {
            throw new KmsCryptoException();
        }
        return key.getActiveVersion().intValue();
    }

    /**
     * 校验尚未进入通用密码服务的参数，并记录拒绝审计。
     */
    private void validateForSignatureAdapter(KmsPrincipal principal, String keyRef, Integer version, String requestId) {
        try {
            if (principal == null || (version != null && version.intValue() < 1)) {
                throw new KmsValidationException();
            }
            KmsValidationHelper.requireKeyRef(keyRef);
        } catch (KmsValidationException exception) {
            if (principal != null) {
                auditPublisher.rejected(principal, keyRef, null, KmsOperation.SIGN, requestId,
                        SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION);
            }
            throw exception;
        }
    }
}
