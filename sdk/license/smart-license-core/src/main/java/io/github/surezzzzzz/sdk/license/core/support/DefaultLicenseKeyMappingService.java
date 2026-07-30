package io.github.surezzzzzz.sdk.license.core.support;

import io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey;
import io.github.surezzzzzz.sdk.kms.client.port.TenantPublicKeyPort;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.LicenseKeyStatus;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseKeyMappingException;
import io.github.surezzzzzz.sdk.license.core.model.LicenseKeyMapping;
import io.github.surezzzzzz.sdk.license.core.repository.LicenseKeyMappingRepository;
import io.github.surezzzzzz.sdk.license.core.service.LicenseKeyMappingService;

import java.util.Arrays;
import java.util.Optional;

/**
 * 默认 License 业务密钥映射服务。
 *
 * @author surezzzzzz
 */
public final class DefaultLicenseKeyMappingService implements LicenseKeyMappingService {

    private final LicenseKeyMappingRepository repository;
    private final TenantPublicKeyPort publicKeyPort;

    /**
     * 创建默认映射服务。
     *
     * @param repository    映射仓储端口
     * @param publicKeyPort KMS 公钥端口
     */
    public DefaultLicenseKeyMappingService(LicenseKeyMappingRepository repository,
                                           TenantPublicKeyPort publicKeyPort) {
        if (repository == null || publicKeyPort == null) {
            throw new LicenseKeyMappingException(ErrorCode.KEY_MAPPING_FAILED,
                    String.format(ErrorMessage.KEY_MAPPING_FAILED, SmartLicenseCoreConstant.DETAIL_CANNOT_BE_NULL));
        }
        this.repository = repository;
        this.publicKeyPort = publicKeyPort;
    }

    private static void assertKmsPublicKey(LicenseKeyMapping mapping, KmsPublicKey publicKey) {
        if (publicKey == null || !mapping.getKmsKeyRef().equals(publicKey.getKeyRef())
                || publicKey.getVersion() == null || publicKey.getVersion().intValue() != mapping.getKmsKeyVersion()
                || !SmartLicenseCoreConstant.ALGORITHM_ES256.equals(publicKey.getAlgorithm())
                || LicenseKeyStatus.fromCode(publicKey.getState()) != mapping.getStatus()
                || !Arrays.equals(mapping.getPublicKey(), publicKey.getPublicKey())) {
            throw mappingException(SmartLicenseCoreConstant.DETAIL_KMS_RESULT_INVALID);
        }
    }

    private static LicenseKeyMappingException mappingException(String detail) {
        return new LicenseKeyMappingException(ErrorCode.KEY_MAPPING_FAILED,
                String.format(ErrorMessage.KEY_MAPPING_FAILED, detail));
    }

    @Override
    public LicenseKeyMapping create(LicenseKeyMapping mapping) {
        if (mapping == null) {
            throw mappingException(SmartLicenseCoreConstant.DETAIL_CANNOT_BE_NULL);
        }
        Optional<LicenseKeyMapping> existing;
        try {
            existing = repository.findByTenantIdAndKid(mapping.getTenantId(), mapping.getKid());
        } catch (RuntimeException exception) {
            throw mappingException(SmartLicenseCoreConstant.DETAIL_KEY_MAPPING_NOT_FOUND);
        }
        if (existing == null) {
            throw mappingException(SmartLicenseCoreConstant.DETAIL_KMS_RESULT_INVALID);
        }
        if (existing.isPresent()) {
            throw mappingException(SmartLicenseCoreConstant.DETAIL_MISMATCH);
        }
        KmsPublicKey publicKey;
        try {
            publicKey = publicKeyPort.read(mapping.getKmsKeyRef(), mapping.getKmsKeyVersion());
        } catch (RuntimeException exception) {
            throw mappingException(SmartLicenseCoreConstant.DETAIL_KMS_RESULT_INVALID);
        }
        assertKmsPublicKey(mapping, publicKey);
        try {
            repository.create(mapping);
        } catch (RuntimeException exception) {
            throw mappingException(SmartLicenseCoreConstant.DETAIL_MISMATCH);
        }
        return mapping;
    }
}
