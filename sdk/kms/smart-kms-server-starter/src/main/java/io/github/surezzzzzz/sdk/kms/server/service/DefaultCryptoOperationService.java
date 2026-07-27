package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.*;
import io.github.surezzzzzz.sdk.kms.core.model.KmsEnvelope;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsCryptoEngine;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyVersionRepository;
import io.github.surezzzzzz.sdk.kms.core.service.CryptoOperationService;
import io.github.surezzzzzz.sdk.kms.core.service.KmsAuthorizationService;
import io.github.surezzzzzz.sdk.kms.core.support.KmsEnvelopeHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * 默认密码学服务编排。
 *
 * @author surezzzzzz
 */
public class DefaultCryptoOperationService implements CryptoOperationService {

    /**
     * 双层授权服务。
     */
    private final KmsAuthorizationService authorizationService;
    /**
     * 逻辑密钥事务锁。
     */
    private final KmsKeyLock keyLock;
    /**
     * 逻辑密钥仓储。
     */
    private final KmsKeyRepository keyRepository;
    /**
     * 密钥版本仓储。
     */
    private final KmsKeyVersionRepository keyVersionRepository;
    /**
     * KMS 可信边界内 JCA 执行端口。
     */
    private final KmsCryptoEngine cryptoEngine;
    /**
     * 以最终随机 IV 构造 SKMS AAD 的内部封装加密端口。
     */
    private final KmsEnvelopeEncryptionEngine envelopeEncryptionEngine;
    /**
     * 成功密码学操作审计发布器。
     */
    private final KmsAuditPublisher auditPublisher;

    /**
     * 创建默认密码学服务。
     */
    public DefaultCryptoOperationService(KmsAuthorizationService authorizationService, KmsKeyLock keyLock,
                                         KmsKeyRepository keyRepository,
                                         KmsKeyVersionRepository keyVersionRepository,
                                         KmsCryptoEngine cryptoEngine,
                                         KmsEnvelopeEncryptionEngine envelopeEncryptionEngine,
                                         KmsAuditPublisher auditPublisher) {
        this.authorizationService = authorizationService;
        this.keyLock = keyLock;
        this.keyRepository = keyRepository;
        this.keyVersionRepository = keyVersionRepository;
        this.cryptoEngine = cryptoEngine;
        this.envelopeEncryptionEngine = envelopeEncryptionEngine;
        this.auditPublisher = auditPublisher;
    }

    /**
     * 校验密钥版本算法。
     */
    private static void requireAlgorithm(KmsKeyVersion keyVersion, KmsAlgorithm algorithm) {
        if (keyVersion.getAlgorithm() != algorithm) {
            throw new KmsCryptoException();
        }
    }

    /**
     * 校验主体与逻辑密钥标识。
     */
    private static void validatePrincipalAndKey(KmsPrincipal principal, String keyRef) {
        if (principal == null) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireKeyRef(keyRef);
    }

    /**
     * 密码学输入允许空字节，但不允许 null。
     */
    private static byte[] requireBytes(byte[] value) {
        if (value == null) {
            throw new KmsValidationException();
        }
        return value;
    }

    /**
     * 执行 ES256 签名。
     */
    @Override
    @Transactional
    public byte[] sign(KmsPrincipal principal, String keyRef, Integer version, byte[] input, String requestId) {
        Integer auditVersion = version;
        try {
            int resolvedVersion = resolveVersion(principal, keyRef, version);
            auditVersion = Integer.valueOf(resolvedVersion);
            authorizationService.authorize(principal, keyRef, resolvedVersion, KmsOperation.SIGN, requestId);
            KmsKeyVersion keyVersion = keyVersion(principal, keyRef, resolvedVersion);
            requireAlgorithm(keyVersion, KmsAlgorithm.ES256);
            byte[] signature = cryptoEngine.sign(KmsAlgorithm.ES256, keyVersion.getPrivateMaterial(), requireBytes(input));
            auditPublisher.allowed(principal, keyRef, auditVersion, KmsOperation.SIGN, requestId,
                    SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION, null, keyVersion.getState(),
                    Integer.valueOf(input.length), Integer.valueOf(signature.length));
            return signature;
        } catch (RuntimeException exception) {
            auditFailure(principal, keyRef, auditVersion, KmsOperation.SIGN, requestId, exception);
            throw exception;
        }
    }

    /**
     * 执行 ES256 验签。
     */
    @Override
    @Transactional
    public boolean verify(KmsPrincipal principal, String keyRef, Integer version, byte[] input,
                          byte[] signature, String requestId) {
        Integer auditVersion = version;
        try {
            int resolvedVersion = resolveVersion(principal, keyRef, version);
            auditVersion = Integer.valueOf(resolvedVersion);
            authorizationService.authorize(principal, keyRef, resolvedVersion, KmsOperation.VERIFY, requestId);
            KmsKeyVersion keyVersion = keyVersion(principal, keyRef, resolvedVersion);
            requireAlgorithm(keyVersion, KmsAlgorithm.ES256);
            boolean valid = cryptoEngine.verify(KmsAlgorithm.ES256, keyVersion.getPublicMaterial(), requireBytes(input),
                    requireBytes(signature));
            auditPublisher.allowed(principal, keyRef, auditVersion, KmsOperation.VERIFY, requestId,
                    SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION, null, keyVersion.getState(),
                    Integer.valueOf(input.length), Integer.valueOf(signature.length));
            return valid;
        } catch (RuntimeException exception) {
            auditFailure(principal, keyRef, auditVersion, KmsOperation.VERIFY, requestId, exception);
            throw exception;
        }
    }

    /**
     * 使用当前活动 AES 版本构造 SKMS 封装并加密。
     */
    @Override
    @Transactional
    public byte[] encrypt(KmsPrincipal principal, String keyRef, byte[] plaintext, byte[] externalAad,
                          String requestId) {
        Integer auditVersion = null;
        try {
            int activeVersion = resolveVersion(principal, keyRef, null);
            auditVersion = Integer.valueOf(activeVersion);
            authorizationService.authorize(principal, keyRef, activeVersion, KmsOperation.ENCRYPT, requestId);
            KmsKeyVersion keyVersion = keyVersion(principal, keyRef, activeVersion);
            requireAlgorithm(keyVersion, KmsAlgorithm.AES_256_GCM);
            byte[] envelope = envelopeEncryptionEngine.encryptEnvelope(keyVersion.getSymmetricMaterial(), keyRef,
                    activeVersion, requireBytes(plaintext), externalAad);
            auditPublisher.allowed(principal, keyRef, auditVersion, KmsOperation.ENCRYPT, requestId,
                    SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION, null, keyVersion.getState(),
                    Integer.valueOf(plaintext.length), Integer.valueOf(envelope.length));
            return envelope;
        } catch (RuntimeException exception) {
            auditFailure(principal, keyRef, auditVersion, KmsOperation.ENCRYPT, requestId, exception);
            throw exception;
        }
    }

    /**
     * 解析 SKMS 封装，绑定其中的版本并解密。
     */
    @Override
    @Transactional
    public byte[] decrypt(KmsPrincipal principal, byte[] envelope, byte[] externalAad, String requestId) {
        KmsEnvelope parsed = KmsEnvelopeHelper.parse(requireBytes(envelope));
        if (parsed.getKeyVersion() > Integer.MAX_VALUE) {
            throw new KmsCryptoException();
        }
        int version = (int) parsed.getKeyVersion();
        try {
            authorizationService.authorize(principal, parsed.getKeyRef(), version, KmsOperation.DECRYPT, requestId);
            KmsKeyVersion keyVersion = keyVersion(principal, parsed.getKeyRef(), version);
            requireAlgorithm(keyVersion, KmsAlgorithm.AES_256_GCM);
            byte[] aad = KmsEnvelopeHelper.buildAad(envelope, externalAad);
            byte[] ciphertext = new byte[parsed.getIv().length + parsed.getCiphertextAndTag().length];
            System.arraycopy(parsed.getIv(), 0, ciphertext, 0, parsed.getIv().length);
            System.arraycopy(parsed.getCiphertextAndTag(), 0, ciphertext, parsed.getIv().length,
                    parsed.getCiphertextAndTag().length);
            try {
                byte[] plaintext = cryptoEngine.decrypt(KmsAlgorithm.AES_256_GCM, keyVersion.getSymmetricMaterial(),
                        ciphertext, aad);
                auditPublisher.allowed(principal, parsed.getKeyRef(), Integer.valueOf(version), KmsOperation.DECRYPT,
                        requestId, SmartKmsCoreConstant.AUDIT_RESOURCE_TYPE_KEY_VERSION, null, keyVersion.getState(),
                        Integer.valueOf(envelope.length), Integer.valueOf(plaintext.length));
                return plaintext;
            } finally {
                Arrays.fill(ciphertext, (byte) 0);
            }
        } catch (RuntimeException exception) {
            auditFailure(principal, parsed.getKeyRef(), Integer.valueOf(version), KmsOperation.DECRYPT, requestId,
                    exception);
            throw exception;
        }
    }

    /**
     * 按异常类别尽力发布已知密钥的拒绝或失败审计事件。
     */
    private void auditFailure(KmsPrincipal principal, String keyRef, Integer keyVersion, KmsOperation operation,
                              String requestId, RuntimeException exception) {
        if (exception instanceof KmsValidationException) {
            auditPublisher.rejected(principal, keyRef, keyVersion, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_VALIDATION);
        } else if (exception instanceof KmsAuthorizationException) {
            auditPublisher.rejected(principal, keyRef, keyVersion, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_AUTHORIZATION);
        } else if (exception instanceof KmsStateConflictException) {
            auditPublisher.rejected(principal, keyRef, keyVersion, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_STATE_CONFLICT);
        } else if (exception instanceof KmsCryptoException) {
            auditPublisher.failed(principal, keyRef, keyVersion, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC);
        } else if (exception instanceof KmsPersistenceException) {
            auditPublisher.failed(principal, keyRef, keyVersion, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_PERSISTENCE);
        } else if (exception instanceof KmsServiceUnavailableException) {
            auditPublisher.failed(principal, keyRef, keyVersion, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
        } else {
            auditPublisher.failed(principal, keyRef, keyVersion, operation, requestId,
                    SmartKmsCoreConstant.AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 解析指定版本或当前活动版本。
     */
    private int resolveVersion(KmsPrincipal principal, String keyRef, Integer version) {
        validatePrincipalAndKey(principal, keyRef);
        if (version != null) {
            if (version.intValue() < 1) {
                throw new KmsValidationException();
            }
            return version.intValue();
        }
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
     * 查询当前 tenant 内精确密钥版本。
     */
    private KmsKeyVersion keyVersion(KmsPrincipal principal, String keyRef, int version) {
        return keyVersionRepository.findByVersion(principal.getTenantId(), keyRef, version)
                .orElseThrow(KmsCryptoException::new);
    }
}
