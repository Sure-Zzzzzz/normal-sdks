package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.*;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyVersionRepository;
import io.github.surezzzzzz.sdk.kms.core.service.KmsAuthorizationService;
import io.github.surezzzzzz.sdk.kms.core.service.PublicKeyService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsStateHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认 ES256 公钥发布服务。
 *
 * @author surezzzzzz
 */
public class DefaultPublicKeyService implements PublicKeyService {

    private final KmsAuthorizationService authorizationService;
    private final KmsKeyLock keyLock;
    private final KmsKeyRepository keyRepository;
    private final KmsKeyVersionRepository keyVersionRepository;
    /**
     * 成功公钥读取审计发布器。
     */
    private final KmsAuditPublisher auditPublisher;

    /**
     * 创建默认公钥发布服务。
     */
    public DefaultPublicKeyService(KmsAuthorizationService authorizationService, KmsKeyLock keyLock,
                                   KmsKeyRepository keyRepository, KmsKeyVersionRepository keyVersionRepository,
                                   KmsAuditPublisher auditPublisher) {
        this.authorizationService = authorizationService;
        this.keyLock = keyLock;
        this.keyRepository = keyRepository;
        this.keyVersionRepository = keyVersionRepository;
        this.auditPublisher = auditPublisher;
    }

    private static KmsPublicKey publicKey(KmsKeyVersion keyVersion) {
        if (keyVersion.getAlgorithm() != KmsAlgorithm.ES256 || keyVersion.getPublicMaterial() == null) {
            throw new KmsCryptoException();
        }
        return new KmsPublicKey(keyVersion.getKeyRef(), keyVersion.getVersion(), keyVersion.getAlgorithm(),
                keyVersion.getState(), keyVersion.getPublicMaterial());
    }

    /**
     * 读取指定可发布版本的公钥。
     */
    @Override
    @Transactional
    public KmsPublicKey read(KmsPrincipal principal, String keyRef, Integer version, String requestId) {
        try {
            int resolvedVersion = resolveVersion(principal, keyRef, version);
            authorizationService.authorize(principal, keyRef, resolvedVersion, KmsOperation.READ_PUBLIC_KEY, requestId);
            KmsKeyVersion keyVersion = keyVersionRepository.findByVersion(principal.getTenantId(), keyRef, resolvedVersion)
                    .orElseThrow(KmsCryptoException::new);
            KmsPublicKey publicKey = publicKey(keyVersion);
            auditPublisher.allowed(principal, keyRef, Integer.valueOf(resolvedVersion), KmsOperation.READ_PUBLIC_KEY,
                    requestId, SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION, null, keyVersion.getState(), null,
                    Integer.valueOf(publicKey.getPublicMaterial().length));
            return publicKey;
        } catch (RuntimeException exception) {
            auditFailure(principal, keyRef, version, requestId, exception);
            throw exception;
        }
    }

    /**
     * 读取逻辑密钥的所有可发布 ES256 公钥。
     */
    @Override
    @Transactional
    public List<KmsPublicKey> list(KmsPrincipal principal, String keyRef, String requestId) {
        try {
            if (principal == null) {
                throw new KmsValidationException();
            }
            KmsValidationHelper.requireKeyRef(keyRef);
            KmsValidationHelper.requireRequestId(requestId);
            KmsKey key = lockedKey(principal, keyRef);
            List<KmsPublicKey> result = new ArrayList<KmsPublicKey>();
            for (KmsKeyVersion version : keyVersionRepository.findByKeyRef(principal.getTenantId(), keyRef)) {
                if (KmsStateHelper.isPublishablePublicKey(key.getState(), version.getState())
                        && version.getAlgorithm() == KmsAlgorithm.ES256) {
                    authorizationService.authorize(principal, keyRef, version.getVersion(), KmsOperation.READ_PUBLIC_KEY,
                            requestId);
                    KmsPublicKey publicKey = publicKey(version);
                    auditPublisher.allowed(principal, keyRef, Integer.valueOf(version.getVersion()),
                            KmsOperation.READ_PUBLIC_KEY, requestId,
                            SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION, null, version.getState(), null,
                            Integer.valueOf(publicKey.getPublicMaterial().length));
                    result.add(publicKey);
                }
            }
            return result;
        } catch (RuntimeException exception) {
            auditFailure(principal, keyRef, null, requestId, exception);
            throw exception;
        }
    }

    /**
     * 按安全分类尽力记录公钥读取拒绝或失败。
     */
    private void auditFailure(KmsPrincipal principal, String keyRef, Integer keyVersion, String requestId,
                              RuntimeException exception) {
        if (exception instanceof KmsValidationException) {
            auditPublisher.rejected(principal, keyRef, keyVersion, KmsOperation.READ_PUBLIC_KEY, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION);
        } else if (exception instanceof KmsAuthorizationException) {
            auditPublisher.rejected(principal, keyRef, keyVersion, KmsOperation.READ_PUBLIC_KEY, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_AUTHORIZATION);
        } else if (exception instanceof KmsStateConflictException) {
            auditPublisher.rejected(principal, keyRef, keyVersion, KmsOperation.READ_PUBLIC_KEY, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_STATE_CONFLICT);
        } else if (exception instanceof KmsCryptoException) {
            auditPublisher.failed(principal, keyRef, keyVersion, KmsOperation.READ_PUBLIC_KEY, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC);
        } else if (exception instanceof KmsPersistenceException) {
            auditPublisher.failed(principal, keyRef, keyVersion, KmsOperation.READ_PUBLIC_KEY, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_PERSISTENCE);
        } else if (exception instanceof KmsServiceUnavailableException) {
            auditPublisher.failed(principal, keyRef, keyVersion, KmsOperation.READ_PUBLIC_KEY, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
        } else {
            auditPublisher.failed(principal, keyRef, keyVersion, KmsOperation.READ_PUBLIC_KEY, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
        }
    }

    private int resolveVersion(KmsPrincipal principal, String keyRef, Integer version) {
        if (principal == null) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireKeyRef(keyRef);
        if (version != null) {
            if (version.intValue() < 1) {
                throw new KmsValidationException();
            }
            return version.intValue();
        }
        KmsKey key = lockedKey(principal, keyRef);
        if (key.getActiveVersion() == null) {
            throw new KmsCryptoException();
        }
        return key.getActiveVersion().intValue();
    }

    /**
     * 在当前事务的逻辑密钥锁内读取密钥元数据。
     */
    private KmsKey lockedKey(KmsPrincipal principal, String keyRef) {
        if (!keyLock.lock(principal.getTenantId(), keyRef)) {
            throw new KmsAuthorizationException();
        }
        return keyRepository.findByKeyRef(principal.getTenantId(), keyRef)
                .orElseThrow(KmsAuthorizationException::new);
    }
}
