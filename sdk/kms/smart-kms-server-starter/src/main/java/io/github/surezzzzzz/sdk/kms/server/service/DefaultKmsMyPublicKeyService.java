package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.*;
import io.github.surezzzzzz.sdk.kms.core.exception.*;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyVersionRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsStateHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 默认人员本人公钥查询；不扩大原公钥和密码学服务的使用策略授权。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultKmsMyPublicKeyService implements KmsMyPublicKeyService {

    /**
     * 同一逻辑密钥的领域行锁。
     */
    private final KmsKeyLock keyLock;
    /**
     * 密钥仓储。
     */
    private final KmsKeyRepository keyRepository;
    /**
     * 版本仓储。
     */
    private final KmsKeyVersionRepository keyVersionRepository;
    /**
     * 安全审计发布器。
     */
    private final KmsAuditPublisher auditPublisher;

    private static void requireHumanPermission(KmsRequestContext context) {
        if (context == null || context.getPrincipal() == null) {
            throw new KmsValidationException();
        }
        KmsPrincipal principal = context.getPrincipal();
        if (!context.isVerifiedHumanSubject() || !principal.getPrincipalId().equals(principal.getOwnerPrincipalId())
                || !principal.hasScope(SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY)) {
            throw new KmsAuthorizationException();
        }
    }

    /**
     * 在领域锁内校验整批版本后读取本人公钥。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<KmsPublicKey> list(KmsRequestContext context, String keyRef) {
        try {
            requireHumanPermission(context);
            KmsValidationHelper.requireKeyRef(keyRef);
            KmsPrincipal principal = context.getPrincipal();
            String owner = principal.getOwnerPrincipalId();
            if (!keyLock.lock(owner, keyRef)) {
                throw new KmsNotFoundException();
            }
            KmsKey key;
            try {
                key = keyRepository.findByKeyRef(owner, keyRef).orElseThrow(KmsNotFoundException::new);
            } catch (KmsValidationException exception) {
                // 请求参数已校验，仓储投影的校验失败属于存储故障。
                throw new KmsPersistenceException();
            }
            if (!owner.equals(key.getOwnerPrincipalId()) || !keyRef.equals(key.getKeyRef())) {
                throw new KmsPersistenceException();
            }
            if (key.getPurpose() != KmsKeyPurpose.SIGN || key.getAlgorithm() != KmsAlgorithm.ES256
                    || (key.getState() != KmsKeyState.ACTIVE && key.getState() != KmsKeyState.DISABLED)) {
                throw new KmsStateConflictException();
            }
            List<KmsPublicKey> result = new ArrayList<KmsPublicKey>();
            boolean activeFound = false;
            Set<Integer> seenVersions = new HashSet<Integer>();
            for (KmsKeyVersion version : keyVersionRepository.findByKeyRef(owner, keyRef)) {
                if (!owner.equals(version.getOwnerPrincipalId()) || !keyRef.equals(version.getKeyRef())
                        || version.getAlgorithm() != KmsAlgorithm.ES256 || version.getVersion() < SmartKmsCoreConstant.ONE
                        || !seenVersions.add(Integer.valueOf(version.getVersion()))) {
                    throw new KmsPersistenceException();
                }
                if (KmsStateHelper.isPublishablePublicKey(key.getState(), version.getState())) {
                    byte[] material = version.getPublicMaterial();
                    if (material == null || material.length == SmartKmsCoreConstant.ZERO) {
                        throw new KmsCryptoException();
                    }
                    result.add(new KmsPublicKey(keyRef, version.getVersion(), version.getAlgorithm(),
                            version.getState(), material));
                    if (version.getState() == KmsKeyVersionState.ACTIVE) {
                        if (activeFound || !Integer.valueOf(version.getVersion()).equals(key.getActiveVersion())) {
                            throw new KmsPersistenceException();
                        }
                        activeFound = true;
                    }
                } else {
                    throw new KmsPersistenceException();
                }
            }
            if (!activeFound || result.isEmpty()) {
                throw new KmsPersistenceException();
            }
            result.sort(Comparator.comparingInt(KmsPublicKey::getVersion));
            for (KmsPublicKey publicKey : result) {
                auditPublisher.allowed(principal, keyRef, Integer.valueOf(publicKey.getVersion()),
                        KmsOperation.READ_PUBLIC_KEY, context.getRequestId(), SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION,
                        key.getState(), publicKey.getState(), null, Integer.valueOf(publicKey.getPublicMaterial().length));
            }
            log.debug("KMS 本人公钥读取完成 requestId={} keyRef={} versions={}",
                    context.getRequestId(), keyRef, result.size());
            return result;
        } catch (RuntimeException exception) {
            auditFailure(context, keyRef, exception);
            throw exception;
        }
    }

    private void auditFailure(KmsRequestContext context, String keyRef, RuntimeException exception) {
        if (context == null || context.getPrincipal() == null) {
            return;
        }
        String category;
        if (exception instanceof KmsAuthorizationException) {
            category = SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_AUTHORIZATION;
        } else if (exception instanceof KmsValidationException) {
            category = SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION;
        } else if (exception instanceof KmsStateConflictException) {
            category = SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_STATE_CONFLICT;
        } else if (exception instanceof KmsNotFoundException) {
            category = SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_AUTHORIZATION;
        } else {
            category = exception instanceof KmsCryptoException ? SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC
                    : exception instanceof KmsPersistenceException ? SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_PERSISTENCE
                    : SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE;
            auditPublisher.failed(context.getPrincipal(), keyRef, null, KmsOperation.READ_PUBLIC_KEY,
                    context.getRequestId(), category);
            log.debug("KMS 本人公钥读取失败 requestId={} keyRef={} category={}", context.getRequestId(), keyRef, category);
            return;
        }
        auditPublisher.rejected(context.getPrincipal(), keyRef, null, KmsOperation.READ_PUBLIC_KEY, context.getRequestId(), category);
        log.debug("KMS 本人公钥读取拒绝 requestId={} keyRef={} category={}", context.getRequestId(), keyRef, category);
    }
}
