package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsStateConflictException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyPolicyRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyVersionRepository;
import io.github.surezzzzzz.sdk.kms.core.service.KmsAuthorizationService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsAuthorizationHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsStateHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 默认双层 scope 与精确策略授权服务。
 *
 * @author surezzzzzz
 */
public class DefaultKmsAuthorizationService implements KmsAuthorizationService {

    /**
     * 逻辑密钥事务锁。
     */
    private final KmsKeyLock keyLock;
    /**
     * 权威数据库时钟。
     */
    private final KmsClock clock;
    /**
     * 逻辑密钥仓储。
     */
    private final KmsKeyRepository keyRepository;
    /**
     * 密钥版本仓储。
     */
    private final KmsKeyVersionRepository keyVersionRepository;
    /**
     * 精确策略仓储。
     */
    private final KmsKeyPolicyRepository keyPolicyRepository;

    /**
     * 创建默认授权服务。
     */
    public DefaultKmsAuthorizationService(KmsKeyLock keyLock, KmsClock clock, KmsKeyRepository keyRepository,
                                          KmsKeyVersionRepository keyVersionRepository,
                                          KmsKeyPolicyRepository keyPolicyRepository) {
        this.keyLock = keyLock;
        this.clock = clock;
        this.keyRepository = keyRepository;
        this.keyVersionRepository = keyVersionRepository;
        this.keyPolicyRepository = keyPolicyRepository;
    }

    /**
     * 校验只允许策略授权的密码学或公钥读取操作。
     */
    private static void validateArguments(KmsPrincipal principal, String keyRef, int version,
                                          KmsOperation operation, String requestId) {
        if (principal == null || version < 1 || operation == null) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireKeyRef(keyRef);
        KmsValidationHelper.requireRequestId(requestId);
        scopeFor(operation);
    }

    /**
     * 将固定 KMS 操作映射到精确 scope。
     */
    private static String scopeFor(KmsOperation operation) {
        if (operation == KmsOperation.SIGN) {
            return SmartKmsServerConstant.SCOPE_SIGN;
        }
        if (operation == KmsOperation.VERIFY) {
            return SmartKmsServerConstant.SCOPE_VERIFY;
        }
        if (operation == KmsOperation.ENCRYPT) {
            return SmartKmsServerConstant.SCOPE_ENCRYPT;
        }
        if (operation == KmsOperation.DECRYPT) {
            return SmartKmsServerConstant.SCOPE_DECRYPT;
        }
        if (operation == KmsOperation.READ_PUBLIC_KEY) {
            return SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY;
        }
        throw new KmsValidationException();
    }

    /**
     * 在同一 key 行锁视图中完成 scope、policy 和状态授权。
     */
    @Override
    @Transactional
    public void authorize(KmsPrincipal principal, String keyRef, int version, KmsOperation operation,
                          String requestId) {
        validateArguments(principal, keyRef, version, operation, requestId);
        if (!principal.hasScope(scopeFor(operation))) {
            throw new KmsAuthorizationException();
        }
        if (!keyLock.lock(principal.getTenantId(), keyRef)) {
            throw new KmsAuthorizationException();
        }
        KmsKey key = keyRepository.findByKeyRef(principal.getTenantId(), keyRef).orElseThrow(KmsAuthorizationException::new);
        KmsKeyVersion keyVersion = keyVersionRepository.findByVersion(principal.getTenantId(), keyRef, version)
                .orElseThrow(KmsAuthorizationException::new);
        if (!KmsStateHelper.canExecute(key.getState(), keyVersion.getState(), operation)) {
            throw new KmsStateConflictException();
        }
        Instant now = clock.now();
        for (KmsKeyPolicy policy : keyPolicyRepository.findByKeyRef(principal.getTenantId(), keyRef)) {
            if (KmsAuthorizationHelper.matches(policy, principal, keyRef, version, operation, now)) {
                return;
            }
        }
        throw new KmsAuthorizationException();
    }
}
